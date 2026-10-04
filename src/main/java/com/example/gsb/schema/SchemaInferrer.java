package com.example.gsb.schema;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型推断器：从一批样本（每行是字段名 -> 样本值）推断字段模式。
 * 缺失字段、null 与空白字符串都按空值处理，不参与类型判定。
 */
public final class SchemaInferrer {

    private final InferenceConfig config;

    public SchemaInferrer() {
        this(InferenceConfig.DEFAULT);
    }

    public SchemaInferrer(InferenceConfig config) {
        this.config = config;
    }

    /**
     * 推断一批样本。产生的警告（高空值占比、冲突等）追加到 warnings。
     *
     * @param rows       样本行；行中缺失的字段按空值计数
     * @param batchIndex 批次序号（用于类型演进历史）
     */
    public Schema inferBatch(List<? extends Map<String, String>> rows, int batchIndex, List<String> warnings) {
        Map<String, FieldStats> statsByField = collectStats(rows);
        Map<String, FieldProfile> profiles = new LinkedHashMap<>();
        for (Map.Entry<String, FieldStats> entry : statsByField.entrySet()) {
            profiles.put(entry.getKey(), finalizeProfile(entry.getKey(), entry.getValue(), batchIndex, warnings));
        }
        return new Schema(profiles);
    }

    public Schema inferBatch(List<? extends Map<String, String>> rows) {
        return inferBatch(rows, 0, new java.util.ArrayList<>());
    }

    private Map<String, FieldStats> collectStats(List<? extends Map<String, String>> rows) {
        Map<String, FieldStats> statsByField = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, String> row : rows) {
                if (row == null) {
                    continue;
                }
                for (String name : row.keySet()) {
                    statsByField.computeIfAbsent(name, k -> new FieldStats(config.maxEnumValuesTracked()));
                }
            }
            for (Map<String, String> row : rows) {
                if (row == null) {
                    continue;
                }
                for (Map.Entry<String, FieldStats> entry : statsByField.entrySet()) {
                    entry.getValue().observe(row.get(entry.getKey()));
                }
            }
        }
        return statsByField;
    }

    private FieldProfile finalizeProfile(String fieldName,
                                         FieldStats stats,
                                         int batchIndex,
                                         List<String> warnings) {
        FieldProfile.Builder builder = FieldProfile.builder(fieldName)
                .totalCount(stats.totalCount())
                .nullCount(stats.nullCount());

        long nonNull = stats.nonNullCount();
        double nullRatio = stats.nullRatio();
        if (nullRatio > config.highNullRatioThreshold()) {
            warnings.add(String.format(
                    "字段 '%s' 空值占比 %.0f%%（%d/%d）超过阈值 %.0f%%，类型推断可信度低",
                    fieldName, nullRatio * 100, stats.nullCount(), stats.totalCount(),
                    config.highNullRatioThreshold() * 100));
        }

        if (nonNull == 0) {
            builder.type(FieldType.UNKNOWN).confidence(0.0);
            builder.addEvolution(TypeEvolution.of(FieldType.UNKNOWN, FieldType.UNKNOWN, batchIndex));
            warnings.add(String.format("字段 '%s' 全部为空值/缺失，无法推断类型", fieldName));
            return builder.build();
        }

        Map<FieldType, Long> typeCounts = stats.typeCounts();
        FieldType dominant = null;
        long dominantCount = -1;
        for (Map.Entry<FieldType, Long> entry : typeCounts.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominantCount = entry.getValue();
                dominant = entry.getKey();
            }
        }

        FieldType mergedType = mergeObservedTypes(typeCounts.keySet());
        if (mergedType == null) {
            double confidence = (double) dominantCount / stats.totalCount();
            builder.type(FieldType.CONFLICT).confidence(round(confidence));
            builder.addEvolution(TypeEvolution.of(FieldType.UNKNOWN, FieldType.CONFLICT, batchIndex));
            warnings.add(String.format(
                    "字段 '%s' 出现无法合并的多种类型 %s，标记为 CONFLICT（主导类型 %s，占非空值 %.0f%%）",
                    fieldName, new LinkedHashSet<>(typeCounts.keySet()), dominant,
                    100.0 * dominantCount / nonNull));
            return builder.build();
        }

        FieldType finalType = mergedType;
        Set<String> enumValues = Set.of();
        if (mergedType == FieldType.STRING && isEnum(stats)) {
            finalType = FieldType.ENUM;
            enumValues = new LinkedHashSet<>(stats.distinctValues());
        }

        double confidence = (double) nonNull / stats.totalCount();
        builder.type(finalType)
                .confidence(round(confidence))
                .enumValues(enumValues)
                .addEvolution(TypeEvolution.of(FieldType.UNKNOWN, finalType, batchIndex));
        return builder.build();
    }

    /**
     * 批内类型合并：全部相同 -> 该类型；INTEGER 与 DECIMAL 混合 -> DECIMAL；否则不可合并（null）。
     */
    static FieldType mergeObservedTypes(Set<FieldType> observed) {
        if (observed.isEmpty()) {
            return null;
        }
        if (observed.size() == 1) {
            return observed.iterator().next();
        }
        boolean allNumeric = observed.stream().allMatch(t -> t == FieldType.INTEGER || t == FieldType.DECIMAL);
        if (allNumeric) {
            return FieldType.DECIMAL;
        }
        return null;
    }

    private boolean isEnum(FieldStats stats) {
        if (stats.distinctOverflow()) {
            return false;
        }
        int distinct = stats.distinctCount();
        return distinct < config.enumMaxDistinctValues()
                && distinct >= 1
                && stats.repetitionRate() >= config.enumMinRepetitionRate();
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
