package com.example.gsb.schemainfer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Main entry point of the component.
 *
 * <p>The engine infers field types from batches of rows (row = ordered map of
 * field name to raw string value; a missing key or a null/blank value counts
 * as null) and reconciles each batch against the schema established by
 * previously accepted batches.
 *
 * <p>Reconciliation never changes the schema silently:
 * <ul>
 *   <li>compatible batches are {@code ACCEPTED} (e.g. an INTEGER batch into a
 *       DECIMAL field, or any atomic batch into a STRING field);</li>
 *   <li>widening changes (INTEGER -&gt; DECIMAL, UNKNOWN -&gt; concrete type,
 *       enum cardinality overflow -&gt; base type) are {@code EVOLVED} and
 *       appended to {@link #getEvolutionHistory()};</li>
 *   <li>incompatible batches (e.g. BOOLEAN values into an INTEGER field) are
 *       {@code REJECTED} and the schema is left untouched.</li>
 * </ul>
 */
public final class SchemaEngine {

    private final InferenceConfig config;
    private final ValueClassifier classifier = new ValueClassifier();
    private final Map<String, FieldSchema> schema = new LinkedHashMap<>();
    private final List<SchemaEvolution> evolutionHistory = new ArrayList<>();
    private final Set<String> conflictedFields = new LinkedHashSet<>();

    public SchemaEngine() {
        this(InferenceConfig.defaults());
    }

    public SchemaEngine(InferenceConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public InferenceConfig getConfig() {
        return config;
    }

    /** Infers field types for a batch without touching the established schema. */
    public BatchInference inferBatch(List<Map<String, String>> rows) {
        Set<String> fieldNames = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            fieldNames.addAll(row.keySet());
        }
        Map<String, FieldAccumulator> accumulators = new LinkedHashMap<>();
        for (String name : fieldNames) {
            accumulators.put(name, new FieldAccumulator(name, config, classifier));
        }
        for (Map<String, String> row : rows) {
            for (String name : fieldNames) {
                accumulators.get(name).accept(row.get(name));
            }
        }
        Map<String, FieldInference> fields = new LinkedHashMap<>();
        for (FieldAccumulator accumulator : accumulators.values()) {
            fields.put(accumulator.getName(), FieldInference.from(accumulator, config));
        }
        return new BatchInference(fields);
    }

    /** Infers the batch and reconciles it against the established schema. */
    public IngestReport ingestBatch(String batchId, List<Map<String, String>> rows) {
        BatchInference inference = inferBatch(rows);
        List<FieldDecision> decisions = new ArrayList<>();
        for (FieldInference fieldInference : inference.getFields().values()) {
            if (fieldInference.hasConflict()) {
                conflictedFields.add(fieldInference.getName());
            }
            FieldSchema existing = schema.get(fieldInference.getName());
            if (existing == null) {
                schema.put(fieldInference.getName(), FieldSchema.fromInference(fieldInference));
                decisions.add(new FieldDecision(fieldInference.getName(), FieldDecision.Status.ADDED,
                        "new field registered as " + fieldInference.getType()));
            } else {
                decisions.add(reconcile(batchId, existing, fieldInference));
            }
        }
        return new IngestReport(batchId, decisions, inference);
    }

    private FieldDecision reconcile(String batchId, FieldSchema established, FieldInference incoming) {
        String name = established.getName();
        FieldType establishedType = established.getType();
        FieldType incomingType = incoming.getType();

        if (incomingType == FieldType.UNKNOWN) {
            established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
            return accept(name, "all-null batch; established type " + establishedType + " unchanged");
        }
        if (establishedType == FieldType.UNKNOWN) {
            established.adopt(incoming);
            recordEvolution(batchId, name, FieldType.UNKNOWN, incomingType,
                    "first non-null batch determines the type");
            return evolve(name, "type resolved from UNKNOWN to " + incomingType);
        }
        if (establishedType == incomingType && establishedType != FieldType.ENUM) {
            established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
            return accept(name, "types match (" + establishedType + ")");
        }
        if (establishedType == FieldType.ENUM) {
            return reconcileEstablishedEnum(batchId, established, incoming);
        }
        if (incomingType == FieldType.ENUM) {
            return reconcileIncomingEnum(batchId, established, incoming);
        }
        if (establishedType.isNumeric() && incomingType.isNumeric()) {
            FieldType merged = widen(establishedType, incomingType);
            if (merged == establishedType) {
                established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
                return accept(name, incomingType + " values are compatible with " + establishedType);
            }
            established.changeType(merged, merged);
            established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
            recordEvolution(batchId, name, establishedType, merged, "numeric widening");
            return evolve(name, "widened " + establishedType + " -> " + merged);
        }
        if (establishedType == FieldType.STRING) {
            established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
            return accept(name, "STRING field accepts " + incomingType + " values");
        }
        return reject(name, "incompatible types: established " + establishedType
                + " vs incoming " + incomingType + "; schema left unchanged");
    }

    private FieldDecision reconcileEstablishedEnum(String batchId, FieldSchema established,
                                                   FieldInference incoming) {
        String name = established.getName();
        FieldType enumBase = established.getBaseType();
        FieldType incomingBase = incoming.getType() == FieldType.ENUM
                ? incoming.getBaseType()
                : incoming.getType();

        if (!enumBaseCompatible(enumBase, incomingBase)) {
            return reject(name, "enum field (base " + enumBase + ") cannot accept "
                    + incomingBase + " batch; schema left unchanged");
        }

        Set<String> union = new LinkedHashSet<>(established.getEnumValues());
        boolean canExtend = !incoming.isDistinctOverflowed();
        if (canExtend) {
            union.addAll(incoming.getDistinctValues());
        }
        if (canExtend && union.size() <= config.getEnumMaxDistinct()) {
            established.addEnumValues(incoming.getDistinctValues());
            established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
            return accept(name, "enum extended to " + union.size() + " value(s)");
        }

        // Cardinality exceeded the threshold: explicitly evolve ENUM -> base type.
        FieldType mergedBase = enumBase == incomingBase ? enumBase
                : (enumBase.isNumeric() && incomingBase.isNumeric()
                        ? widen(enumBase, incomingBase)
                        : FieldType.STRING);
        established.changeType(mergedBase, mergedBase);
        established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
        recordEvolution(batchId, name, FieldType.ENUM, mergedBase,
                "enum cardinality exceeded threshold " + config.getEnumMaxDistinct());
        return evolve(name, "ENUM -> " + mergedBase + " (cardinality exceeded threshold)");
    }

    private FieldDecision reconcileIncomingEnum(String batchId, FieldSchema established,
                                                FieldInference incoming) {
        String name = established.getName();
        FieldType establishedType = established.getType();
        FieldType enumBase = incoming.getBaseType();

        boolean compatible = establishedType == FieldType.STRING
                || establishedType == enumBase
                || (establishedType.isNumeric() && enumBase.isNumeric()
                        && widen(establishedType, enumBase) == establishedType);
        if (compatible) {
            established.addCounts(incoming.getTotalCount(), incoming.getNullCount());
            return accept(name, "enum batch (base " + enumBase + ") is compatible with "
                    + establishedType + " field");
        }
        return reject(name, "enum batch (base " + enumBase + ") is incompatible with "
                + establishedType + " field; schema left unchanged");
    }

    private boolean enumBaseCompatible(FieldType enumBase, FieldType incomingBase) {
        return enumBase == incomingBase
                || (enumBase.isNumeric() && incomingBase.isNumeric())
                || enumBase == FieldType.STRING;
    }

    private static FieldType widen(FieldType left, FieldType right) {
        return (left == FieldType.DECIMAL || right == FieldType.DECIMAL)
                ? FieldType.DECIMAL
                : FieldType.INTEGER;
    }

    private void recordEvolution(String batchId, String field, FieldType from, FieldType to,
                                 String reason) {
        evolutionHistory.add(new SchemaEvolution(batchId, field, from, to, reason, Instant.now()));
    }

    private FieldDecision accept(String field, String message) {
        return new FieldDecision(field, FieldDecision.Status.ACCEPTED, message);
    }

    private FieldDecision evolve(String field, String message) {
        return new FieldDecision(field, FieldDecision.Status.EVOLVED, message);
    }

    private FieldDecision reject(String field, String message) {
        conflictedFields.add(field);
        return new FieldDecision(field, FieldDecision.Status.REJECTED, message);
    }

    /** Unmodifiable view of the established schema, keyed by field name. */
    public Map<String, FieldSchema> getSchema() {
        return Collections.unmodifiableMap(schema);
    }

    public FieldSchema getFieldSchema(String name) {
        return schema.get(name);
    }

    /** Chronological history of every explicit schema change. */
    public List<SchemaEvolution> getEvolutionHistory() {
        return Collections.unmodifiableList(evolutionHistory);
    }

    /** Names of fields that ever produced an unresolvable type conflict. */
    public Set<String> getConflictedFields() {
        return Collections.unmodifiableSet(conflictedFields);
    }

    public SchemaStatistics getStatistics() {
        Map<FieldType, Long> distribution = new EnumMap<>(FieldType.class);
        int enumFields = 0;
        for (FieldSchema field : schema.values()) {
            distribution.merge(field.getType(), 1L, Long::sum);
            if (field.isEnum()) {
                enumFields++;
            }
        }
        return new SchemaStatistics(schema.size(), Collections.unmodifiableMap(distribution),
                conflictedFields.size(), enumFields);
    }
}
