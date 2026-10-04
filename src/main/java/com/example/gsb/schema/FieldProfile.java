package com.example.gsb.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单个字段的推断结果（模式）：最终类型、置信度、空值统计、枚举取值与类型演进历史。
 */
public final class FieldProfile {

    private final String name;
    private final FieldType type;
    private final double confidence;
    private final long totalCount;
    private final long nullCount;
    private final Set<String> enumValues;
    private final List<TypeEvolution> typeHistory;

    private FieldProfile(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.confidence = builder.confidence;
        this.totalCount = builder.totalCount;
        this.nullCount = builder.nullCount;
        this.enumValues = Collections.unmodifiableSet(new LinkedHashSet<>(builder.enumValues));
        this.typeHistory = Collections.unmodifiableList(new ArrayList<>(builder.typeHistory));
    }

    public String name() {
        return name;
    }

    public FieldType type() {
        return type;
    }

    /** 置信度，范围 [0,1]。 */
    public double confidence() {
        return confidence;
    }

    public long totalCount() {
        return totalCount;
    }

    public long nullCount() {
        return nullCount;
    }

    public double nullRatio() {
        return totalCount == 0 ? 0.0 : (double) nullCount / totalCount;
    }

    /** 枚举取值（仅当 type 为 ENUM 时非空）。 */
    public Set<String> enumValues() {
        return enumValues;
    }

    /** 类型演进历史（含初始类型记录）。 */
    public List<TypeEvolution> typeHistory() {
        return typeHistory;
    }

    public boolean isConflict() {
        return type == FieldType.CONFLICT;
    }

    public boolean isEnum() {
        return type == FieldType.ENUM;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(name);
        builder.type = this.type;
        builder.confidence = this.confidence;
        builder.totalCount = this.totalCount;
        builder.nullCount = this.nullCount;
        builder.enumValues.addAll(this.enumValues);
        builder.typeHistory.addAll(this.typeHistory);
        return builder;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private FieldType type = FieldType.UNKNOWN;
        private double confidence = 0.0;
        private long totalCount;
        private long nullCount;
        private final Set<String> enumValues = new LinkedHashSet<>();
        private final List<TypeEvolution> typeHistory = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder type(FieldType type) {
            this.type = type;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder totalCount(long totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public Builder nullCount(long nullCount) {
            this.nullCount = nullCount;
            return this;
        }

        public Builder enumValues(Set<String> values) {
            this.enumValues.clear();
            this.enumValues.addAll(values);
            return this;
        }

        public Builder addEnumValues(Set<String> values) {
            this.enumValues.addAll(values);
            return this;
        }

        public Builder typeHistory(List<TypeEvolution> history) {
            this.typeHistory.clear();
            this.typeHistory.addAll(history);
            return this;
        }

        public Builder addEvolution(TypeEvolution evolution) {
            this.typeHistory.add(evolution);
            return this;
        }

        public FieldProfile build() {
            return new FieldProfile(this);
        }
    }
}
