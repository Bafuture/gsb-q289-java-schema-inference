package com.example.gsb.schema;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 单个字段在一批样本中的累计统计：总数、空值数、各类型命中数、去重取值（有上限）。
 */
public final class FieldStats {

    private final int maxEnumValuesTracked;
    private long totalCount;
    private long nullCount;
    private final Map<FieldType, Long> typeCounts = new EnumMap<>(FieldType.class);
    private final Set<String> distinctValues = new LinkedHashSet<>();
    private boolean distinctOverflow;

    public FieldStats() {
        this(InferenceConfig.DEFAULT.maxEnumValuesTracked());
    }

    public FieldStats(int maxEnumValuesTracked) {
        this.maxEnumValuesTracked = maxEnumValuesTracked;
    }

    /** 记录一条样本值；value 为 null 或空白视为空值。 */
    public void observe(String value) {
        totalCount++;
        if (value == null || value.trim().isEmpty()) {
            nullCount++;
            return;
        }
        String trimmed = value.trim();
        FieldType type = ValueTypeClassifier.classify(trimmed);
        typeCounts.merge(type, 1L, Long::sum);
        if (distinctValues.size() < maxEnumValuesTracked || distinctValues.contains(trimmed)) {
            distinctValues.add(trimmed);
        } else {
            distinctOverflow = true;
        }
    }

    public long totalCount() {
        return totalCount;
    }

    public long nullCount() {
        return nullCount;
    }

    public long nonNullCount() {
        return totalCount - nullCount;
    }

    public double nullRatio() {
        return totalCount == 0 ? 0.0 : (double) nullCount / totalCount;
    }

    public Map<FieldType, Long> typeCounts() {
        return Collections.unmodifiableMap(typeCounts);
    }

    public Set<String> distinctValues() {
        return Collections.unmodifiableSet(distinctValues);
    }

    public int distinctCount() {
        return distinctValues.size();
    }

    public boolean distinctOverflow() {
        return distinctOverflow;
    }

    /** 重复率 = 1 - distinct/nonNull；nonNull 为 0 时返回 0。 */
    public double repetitionRate() {
        long nonNull = nonNullCount();
        return nonNull == 0 ? 0.0 : 1.0 - (double) distinctValues.size() / nonNull;
    }

    /** 合并另一份统计（用于跨批次累计）。 */
    public void mergeFrom(FieldStats other) {
        this.totalCount += other.totalCount;
        this.nullCount += other.nullCount;
        other.typeCounts.forEach((type, count) -> this.typeCounts.merge(type, count, Long::sum));
        for (String value : other.distinctValues) {
            if (this.distinctValues.size() < maxEnumValuesTracked || this.distinctValues.contains(value)) {
                this.distinctValues.add(value);
            } else {
                this.distinctOverflow = true;
                break;
            }
        }
        this.distinctOverflow = this.distinctOverflow || other.distinctOverflow;
    }
}
