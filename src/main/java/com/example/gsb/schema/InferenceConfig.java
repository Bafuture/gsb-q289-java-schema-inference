package com.example.gsb.schema;

import java.util.Objects;

/**
 * 推断配置。所有阈值均可通过 withXxx 方法调整（返回新实例，原实例不变）。
 */
public final class InferenceConfig {

    public static final InferenceConfig DEFAULT = new InferenceConfig(20, 0.5, 0.5, 1000);

    /** 取值种类数小于该阈值才可能识别为枚举。 */
    private final int enumMaxDistinctValues;
    /** 重复率（1 - distinct/nonNull）不低于该值才可能识别为枚举。 */
    private final double enumMinRepetitionRate;
    /** 空值占比超过该值时产生警告，类型标记为 UNKNOWN。 */
    private final double highNullRatioThreshold;
    /** 枚举取值缓存上限，防止无限增长。 */
    private final int maxEnumValuesTracked;

    public InferenceConfig(int enumMaxDistinctValues,
                           double enumMinRepetitionRate,
                           double highNullRatioThreshold,
                           int maxEnumValuesTracked) {
        if (enumMaxDistinctValues < 1) {
            throw new IllegalArgumentException("enumMaxDistinctValues must be >= 1");
        }
        if (enumMinRepetitionRate < 0 || enumMinRepetitionRate > 1) {
            throw new IllegalArgumentException("enumMinRepetitionRate must be in [0,1]");
        }
        if (highNullRatioThreshold < 0 || highNullRatioThreshold > 1) {
            throw new IllegalArgumentException("highNullRatioThreshold must be in [0,1]");
        }
        if (maxEnumValuesTracked < 1) {
            throw new IllegalArgumentException("maxEnumValuesTracked must be >= 1");
        }
        this.enumMaxDistinctValues = enumMaxDistinctValues;
        this.enumMinRepetitionRate = enumMinRepetitionRate;
        this.highNullRatioThreshold = highNullRatioThreshold;
        this.maxEnumValuesTracked = maxEnumValuesTracked;
    }

    public int enumMaxDistinctValues() {
        return enumMaxDistinctValues;
    }

    public double enumMinRepetitionRate() {
        return enumMinRepetitionRate;
    }

    public double highNullRatioThreshold() {
        return highNullRatioThreshold;
    }

    public int maxEnumValuesTracked() {
        return maxEnumValuesTracked;
    }

    public InferenceConfig withEnumMaxDistinctValues(int value) {
        return new InferenceConfig(value, enumMinRepetitionRate, highNullRatioThreshold, maxEnumValuesTracked);
    }

    public InferenceConfig withEnumMinRepetitionRate(double value) {
        return new InferenceConfig(enumMaxDistinctValues, value, highNullRatioThreshold, maxEnumValuesTracked);
    }

    public InferenceConfig withHighNullRatioThreshold(double value) {
        return new InferenceConfig(enumMaxDistinctValues, enumMinRepetitionRate, value, maxEnumValuesTracked);
    }

    public InferenceConfig withMaxEnumValuesTracked(int value) {
        return new InferenceConfig(enumMaxDistinctValues, enumMinRepetitionRate, highNullRatioThreshold, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InferenceConfig other)) {
            return false;
        }
        return enumMaxDistinctValues == other.enumMaxDistinctValues
                && Double.compare(enumMinRepetitionRate, other.enumMinRepetitionRate) == 0
                && Double.compare(highNullRatioThreshold, other.highNullRatioThreshold) == 0
                && maxEnumValuesTracked == other.maxEnumValuesTracked;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enumMaxDistinctValues, enumMinRepetitionRate, highNullRatioThreshold, maxEnumValuesTracked);
    }
}
