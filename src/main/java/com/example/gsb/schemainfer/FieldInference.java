package com.example.gsb.schemainfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inference result for one field within a batch.
 *
 * <p>{@link #getType()} is the resolved type (possibly {@link FieldType#ENUM}
 * or {@link FieldType#UNKNOWN}); {@link #getBaseType()} is the underlying
 * atomic type (equal to {@code type} unless the field was detected as an enum).
 *
 * <p>{@link #getConfidence()} is the fraction of non-null values consistent
 * with the resolved type/group, in {@code [0, 1]}. Values belonging to an
 * incompatible type group are listed in {@link #getConflictingTypes()}.
 */
public final class FieldInference {

    private final String name;
    private final FieldType type;
    private final FieldType baseType;
    private final double confidence;
    private final long totalCount;
    private final long nullCount;
    private final double nullRatio;
    private final boolean mostlyNull;
    private final Set<String> enumValues;
    private final List<FieldType> conflictingTypes;
    private final boolean distinctOverflowed;
    private final Set<String> distinctValues;

    private FieldInference(String name, FieldType type, FieldType baseType, double confidence,
                           long totalCount, long nullCount, double nullRatio, boolean mostlyNull,
                           Set<String> enumValues, List<FieldType> conflictingTypes,
                           boolean distinctOverflowed, Set<String> distinctValues) {
        this.name = name;
        this.type = type;
        this.baseType = baseType;
        this.confidence = confidence;
        this.totalCount = totalCount;
        this.nullCount = nullCount;
        this.nullRatio = nullRatio;
        this.mostlyNull = mostlyNull;
        this.enumValues = enumValues;
        this.conflictingTypes = conflictingTypes;
        this.distinctOverflowed = distinctOverflowed;
        this.distinctValues = distinctValues;
    }

    static FieldInference from(FieldAccumulator accumulator, InferenceConfig config) {
        long total = accumulator.getTotalCount();
        long nulls = accumulator.getNullCount();
        long nonNull = total - nulls;
        double ratio = total == 0 ? 0.0 : (double) nulls / total;
        boolean mostlyNull = total > 0 && ratio >= config.getHighNullRatioThreshold();

        if (nonNull == 0) {
            return new FieldInference(accumulator.getName(), FieldType.UNKNOWN, FieldType.UNKNOWN,
                    0.0, total, nulls, ratio, mostlyNull, Set.of(), List.of(),
                    accumulator.isDistinctOverflowed(), Set.of());
        }

        Map<FieldType, Long> counts = accumulator.getTypeCounts();
        long integers = counts.getOrDefault(FieldType.INTEGER, 0L);
        long decimals = counts.getOrDefault(FieldType.DECIMAL, 0L);
        long booleans = counts.getOrDefault(FieldType.BOOLEAN, 0L);
        long dateTimes = counts.getOrDefault(FieldType.DATETIME, 0L);
        long strings = counts.getOrDefault(FieldType.STRING, 0L);

        // Type groups that can merge: INTEGER + DECIMAL -> the numeric group.
        // On a tie the order below acts as the deterministic priority.
        long numeric = integers + decimals;
        FieldType dominantGroup = FieldType.STRING;
        long dominantCount = strings;
        if (numeric > dominantCount) {
            dominantGroup = FieldType.DECIMAL;
            dominantCount = numeric;
        }
        if (booleans > dominantCount) {
            dominantGroup = FieldType.BOOLEAN;
            dominantCount = booleans;
        }
        if (dateTimes > dominantCount) {
            dominantGroup = FieldType.DATETIME;
            dominantCount = dateTimes;
        }

        FieldType mergedBase;
        switch (dominantGroup) {
            case DECIMAL -> mergedBase = decimals > 0 ? FieldType.DECIMAL : FieldType.INTEGER;
            case BOOLEAN -> mergedBase = FieldType.BOOLEAN;
            case DATETIME -> mergedBase = FieldType.DATETIME;
            default -> mergedBase = FieldType.STRING;
        }

        List<FieldType> conflicts = new ArrayList<>();
        addIfPresent(conflicts, counts, FieldType.INTEGER, mergedBase);
        addIfPresent(conflicts, counts, FieldType.DECIMAL, mergedBase);
        addIfPresent(conflicts, counts, FieldType.BOOLEAN, mergedBase);
        addIfPresent(conflicts, counts, FieldType.DATETIME, mergedBase);
        addIfPresent(conflicts, counts, FieldType.STRING, mergedBase);

        double confidence = (double) dominantCount / nonNull;

        Set<String> distinct = accumulator.getDistinctValues();
        boolean enumLike = conflicts.isEmpty()
                && (mergedBase == FieldType.STRING || mergedBase == FieldType.INTEGER)
                && !accumulator.isDistinctOverflowed()
                && distinct.size() <= config.getEnumMaxDistinct()
                && nonNull >= config.getEnumMinSamples()
                && 1.0 - (double) distinct.size() / nonNull >= config.getEnumMinRepetitionRatio();

        FieldType resolvedType = enumLike ? FieldType.ENUM : mergedBase;
        Set<String> enumValues = enumLike
                ? Collections.unmodifiableSet(new LinkedHashSet<>(distinct))
                : Set.of();

        return new FieldInference(accumulator.getName(), resolvedType, mergedBase, confidence,
                total, nulls, ratio, mostlyNull, enumValues,
                Collections.unmodifiableList(conflicts),
                accumulator.isDistinctOverflowed(),
                Collections.unmodifiableSet(new LinkedHashSet<>(distinct)));
    }

    private static void addIfPresent(List<FieldType> conflicts, Map<FieldType, Long> counts,
                                     FieldType candidate, FieldType mergedBase) {
        if (counts.getOrDefault(candidate, 0L) == 0L) {
            return;
        }
        boolean inNumericGroup = mergedBase.isNumeric() && candidate.isNumeric();
        if (candidate != mergedBase && !inNumericGroup) {
            conflicts.add(candidate);
        }
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public FieldType getBaseType() {
        return baseType;
    }

    public double getConfidence() {
        return confidence;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public long getNullCount() {
        return nullCount;
    }

    public long getNonNullCount() {
        return totalCount - nullCount;
    }

    public double getNullRatio() {
        return nullRatio;
    }

    /**
     * True when the null ratio is at or above {@code highNullRatioThreshold}.
     * The type is still derived from the non-null samples, but the low volume
     * of evidence should be surfaced to downstream consumers.
     */
    public boolean isMostlyNull() {
        return mostlyNull;
    }

    public boolean isEnum() {
        return type == FieldType.ENUM;
    }

    public boolean hasConflict() {
        return !conflictingTypes.isEmpty();
    }

    public List<FieldType> getConflictingTypes() {
        return conflictingTypes;
    }

    public Set<String> getEnumValues() {
        return enumValues;
    }

    boolean isDistinctOverflowed() {
        return distinctOverflowed;
    }

    Set<String> getDistinctValues() {
        return distinctValues;
    }
}
