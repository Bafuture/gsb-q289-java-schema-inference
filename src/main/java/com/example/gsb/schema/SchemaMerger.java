package com.example.gsb.schema;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 跨批次合并器：把新批次的模式并入既有模式。
 * 支持按优先级扩展（INTEGER -> DECIMAL、ENUM -> STRING），无法合并时按策略处理：
 * EXPAND 下标记 CONFLICT 并报告，STRICT 下明确拒绝（保留既有类型不变）。
 */
public final class SchemaMerger {

    private final MergePolicy policy;

    public SchemaMerger() {
        this(MergePolicy.EXPAND);
    }

    public SchemaMerger(MergePolicy policy) {
        this.policy = policy;
    }

    /**
     * 合并既有模式与新批次模式。
     *
     * @param existing   既有模式（首批次可为 null）
     * @param incoming   新批次推断结果
     * @param batchIndex 新批次序号（写入类型演进历史）
     * @param warnings   收集冲突/拒绝/演进/高空值警告
     * @return 合并结果（含新模式与冲突字段集合）
     */
    public SchemaMergeResult merge(Schema existing, Schema incoming, int batchIndex, java.util.List<String> warnings) {
        Map<String, FieldProfile> merged = new LinkedHashMap<>();
        Set<String> conflicts = new LinkedHashSet<>();
        Set<String> rejected = new LinkedHashSet<>();

        if (existing != null) {
            merged.putAll(existing.fields());
        }

        for (Map.Entry<String, FieldProfile> entry : incoming.fields().entrySet()) {
            String name = entry.getKey();
            FieldProfile incomingProfile = entry.getValue();
            FieldProfile currentProfile = merged.get(name);
            if (currentProfile == null) {
                merged.put(name, incomingProfile);
                continue;
            }
            FieldProfile nextProfile = mergeField(currentProfile, incomingProfile, batchIndex, warnings, conflicts, rejected);
            merged.put(name, nextProfile);
        }
        return new SchemaMergeResult(new Schema(merged), conflicts, rejected, warnings);
    }

    private FieldProfile mergeField(FieldProfile current,
                                    FieldProfile incoming,
                                    int batchIndex,
                                    java.util.List<String> warnings,
                                    Set<String> conflicts,
                                    Set<String> rejected) {
        FieldType currentType = current.type();
        FieldType incomingType = incoming.type();

        if (currentType == FieldType.UNKNOWN) {
            return incoming.toBuilder()
                    .totalCount(current.totalCount() + incoming.totalCount())
                    .nullCount(current.nullCount() + incoming.nullCount())
                    .typeHistory(mergeHistory(current, incoming))
                    .build();
        }

        if (currentType == FieldType.CONFLICT) {
            if (currentType != incomingType) {
                warnings.add(String.format(
                        "字段 '%s' 已处于 CONFLICT 状态，新批次类型 %s 不能消解冲突，继续保留 CONFLICT",
                        current.name(), incomingType));
            }
            return aggregateCounts(current, incoming);
        }

        if (incomingType == FieldType.UNKNOWN) {
            return aggregateCounts(current, incoming);
        }

        if (incomingType == FieldType.CONFLICT) {
            if (policy == MergePolicy.STRICT) {
                rejected.add(current.name());
                warnings.add(String.format(
                        "STRICT: 字段 '%s' 新批次内部存在类型冲突，拒绝改动，既有类型保持 %s",
                        current.name(), currentType));
                return aggregateCounts(current, incoming);
            }
            conflicts.add(current.name());
            warnings.add(String.format(
                    "EXPAND: 字段 '%s' 新批次内部存在类型冲突，标记为 CONFLICT", current.name()));
            return current.toBuilder()
                    .type(FieldType.CONFLICT)
                    .confidence(0.0)
                    .totalCount(current.totalCount() + incoming.totalCount())
                    .nullCount(current.nullCount() + incoming.nullCount())
                    .addEvolution(TypeEvolution.of(currentType, FieldType.CONFLICT, batchIndex))
                    .build();
        }

        if (currentType == incomingType) {
            FieldProfile.Builder builder = aggregateCountsBuilder(current, incoming);
            if (currentType == FieldType.ENUM) {
                Set<String> combined = new LinkedHashSet<>(current.enumValues());
                combined.addAll(incoming.enumValues());
                builder.enumValues(combined);
            }
            return builder.build();
        }

        FieldType widened = widen(currentType, incomingType);
        if (widened != null) {
            if (policy == MergePolicy.STRICT && widened != currentType) {
                rejected.add(current.name());
                warnings.add(String.format(
                        "STRICT: 字段 '%s' 类型变化 %s -> %s 被拒绝，既有类型保持 %s（不做静默修改）",
                        current.name(), currentType, incomingType, currentType));
                return aggregateCounts(current, incoming);
            }
            warnings.add(String.format(
                    "字段 '%s' 模式演进：%s -> %s（批次 %d）", current.name(), currentType, widened, batchIndex));
            FieldProfile.Builder builder = aggregateCountsBuilder(current, incoming)
                    .type(widened)
                    .addEvolution(evolution(currentType, widened, batchIndex, current.enumValues()));
            if (widened != FieldType.ENUM) {
                builder.enumValues(Set.of());
            }
            return builder.build();
        }

        String message = String.format(
                "字段 '%s' 类型冲突：既有 %s 与新批次 %s 无法合并", current.name(), currentType, incomingType);
        if (policy == MergePolicy.STRICT) {
            rejected.add(current.name());
            warnings.add("STRICT: " + message + "，拒绝改动，既有类型保持 " + currentType + "（不做静默修改）");
            return aggregateCounts(current, incoming);
        }
        conflicts.add(current.name());
        warnings.add("EXPAND: " + message + "，标记为 CONFLICT");
        return current.toBuilder()
                .type(FieldType.CONFLICT)
                .confidence(0.0)
                .totalCount(current.totalCount() + incoming.totalCount())
                .nullCount(current.nullCount() + incoming.nullCount())
                .addEvolution(TypeEvolution.of(currentType, FieldType.CONFLICT, batchIndex))
                .build();
    }

    /** 允许的模式扩展方向。 */
    private static FieldType widen(FieldType current, FieldType incoming) {
        if (current == FieldType.INTEGER && incoming == FieldType.DECIMAL) {
            return FieldType.DECIMAL;
        }
        if (current == FieldType.ENUM && incoming == FieldType.STRING) {
            return FieldType.STRING;
        }
        return null;
    }

    private static TypeEvolution evolution(FieldType from, FieldType to, int batchIndex, Set<String> previousEnumValues) {
        if (from == FieldType.ENUM) {
            return TypeEvolution.ofEnum(from, to, batchIndex, previousEnumValues);
        }
        return TypeEvolution.of(from, to, batchIndex);
    }

    private static FieldProfile aggregateCounts(FieldProfile current, FieldProfile incoming) {
        return aggregateCountsBuilder(current, incoming).build();
    }

    private static FieldProfile.Builder aggregateCountsBuilder(FieldProfile current, FieldProfile incoming) {
        long total = current.totalCount() + incoming.totalCount();
        long nulls = current.nullCount() + incoming.nullCount();
        double confidence = total == 0 ? 0.0 : Math.round((double) (total - nulls) / total * 10000.0) / 10000.0;
        return current.toBuilder()
                .totalCount(total)
                .nullCount(nulls)
                .confidence(confidence)
                .typeHistory(mergeHistory(current, incoming));
    }

    private static java.util.List<TypeEvolution> mergeHistory(FieldProfile current, FieldProfile incoming) {
        java.util.List<TypeEvolution> history =
                new java.util.ArrayList<>(current.typeHistory());
        history.addAll(incoming.typeHistory());
        return history;
    }
}
