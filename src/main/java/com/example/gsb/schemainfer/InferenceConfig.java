package com.example.gsb.schemainfer;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Tunable thresholds for type inference.
 *
 * <ul>
 *   <li>{@code enumMaxDistinct} – a field is eligible for {@link FieldType#ENUM}
 *       only when the number of distinct non-null values does not exceed this
 *       threshold.</li>
 *   <li>{@code enumMinRepetitionRatio} – fraction of non-null values that are
 *       duplicates of other values ({@code 1 - distinct/nonNull}) required for
 *       enum detection.</li>
 *   <li>{@code enumMinSamples} – minimum number of non-null samples before enum
 *       detection is attempted, to avoid false positives on tiny batches.</li>
 *   <li>{@code highNullRatioThreshold} – null ratio at or above which a field is
 *       flagged as "mostly null". The field is still typed from its non-null
 *       samples, but consumers are warned that the evidence is thin.</li>
 *   <li>{@code nullTokens} – case-insensitive tokens (in addition to
 *       {@code null} and blank strings) treated as null values.</li>
 * </ul>
 */
public final class InferenceConfig {

    public static final int DEFAULT_ENUM_MAX_DISTINCT = 20;
    public static final double DEFAULT_ENUM_MIN_REPETITION_RATIO = 0.5;
    public static final int DEFAULT_ENUM_MIN_SAMPLES = 5;
    public static final double DEFAULT_HIGH_NULL_RATIO_THRESHOLD = 0.8;

    private final int enumMaxDistinct;
    private final double enumMinRepetitionRatio;
    private final int enumMinSamples;
    private final double highNullRatioThreshold;
    private final Set<String> nullTokens;

    private InferenceConfig(Builder builder) {
        this.enumMaxDistinct = builder.enumMaxDistinct;
        this.enumMinRepetitionRatio = builder.enumMinRepetitionRatio;
        this.enumMinSamples = builder.enumMinSamples;
        this.highNullRatioThreshold = builder.highNullRatioThreshold;
        this.nullTokens = Set.copyOf(builder.nullTokens);
    }

    public static InferenceConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getEnumMaxDistinct() {
        return enumMaxDistinct;
    }

    public double getEnumMinRepetitionRatio() {
        return enumMinRepetitionRatio;
    }

    public int getEnumMinSamples() {
        return enumMinSamples;
    }

    public double getHighNullRatioThreshold() {
        return highNullRatioThreshold;
    }

    public Set<String> getNullTokens() {
        return nullTokens;
    }

    /** Returns {@code true} when the raw value should be treated as null/missing. */
    public boolean isNullValue(String raw) {
        if (raw == null) {
            return true;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return nullTokens.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    public static final class Builder {
        private int enumMaxDistinct = DEFAULT_ENUM_MAX_DISTINCT;
        private double enumMinRepetitionRatio = DEFAULT_ENUM_MIN_REPETITION_RATIO;
        private int enumMinSamples = DEFAULT_ENUM_MIN_SAMPLES;
        private double highNullRatioThreshold = DEFAULT_HIGH_NULL_RATIO_THRESHOLD;
        private Set<String> nullTokens = new LinkedHashSet<>(Set.of("null", "n/a"));

        public Builder enumMaxDistinct(int enumMaxDistinct) {
            if (enumMaxDistinct < 1) {
                throw new IllegalArgumentException("enumMaxDistinct must be >= 1");
            }
            this.enumMaxDistinct = enumMaxDistinct;
            return this;
        }

        public Builder enumMinRepetitionRatio(double enumMinRepetitionRatio) {
            if (enumMinRepetitionRatio < 0.0 || enumMinRepetitionRatio > 1.0) {
                throw new IllegalArgumentException("enumMinRepetitionRatio must be in [0, 1]");
            }
            this.enumMinRepetitionRatio = enumMinRepetitionRatio;
            return this;
        }

        public Builder enumMinSamples(int enumMinSamples) {
            if (enumMinSamples < 1) {
                throw new IllegalArgumentException("enumMinSamples must be >= 1");
            }
            this.enumMinSamples = enumMinSamples;
            return this;
        }

        public Builder highNullRatioThreshold(double highNullRatioThreshold) {
            if (highNullRatioThreshold < 0.0 || highNullRatioThreshold > 1.0) {
                throw new IllegalArgumentException("highNullRatioThreshold must be in [0, 1]");
            }
            this.highNullRatioThreshold = highNullRatioThreshold;
            return this;
        }

        public Builder nullTokens(Set<String> tokens) {
            Objects.requireNonNull(tokens, "tokens");
            Set<String> normalized = new LinkedHashSet<>();
            for (String token : tokens) {
                normalized.add(token.toLowerCase(Locale.ROOT));
            }
            this.nullTokens = normalized;
            return this;
        }

        public InferenceConfig build() {
            return new InferenceConfig(this);
        }
    }
}
