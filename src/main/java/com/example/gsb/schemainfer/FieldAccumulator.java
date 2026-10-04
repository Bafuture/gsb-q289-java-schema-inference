package com.example.gsb.schemainfer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-field statistics accumulated while scanning a single batch.
 *
 * <p>Distinct values are tracked only up to {@code enumMaxDistinct + 1}: once
 * the threshold is exceeded the set is discarded and {@code distinctOverflowed}
 * is set, which both bounds memory and proves that the field cannot be an enum.
 */
final class FieldAccumulator {

    private final String name;
    private final InferenceConfig config;
    private final ValueClassifier classifier;

    private long totalCount;
    private long nullCount;
    private final EnumMap<FieldType, Long> typeCounts = new EnumMap<>(FieldType.class);
    private final Set<String> distinctValues = new LinkedHashSet<>();
    private boolean distinctOverflowed;

    FieldAccumulator(String name, InferenceConfig config, ValueClassifier classifier) {
        this.name = name;
        this.config = config;
        this.classifier = classifier;
    }

    void accept(String rawValue) {
        totalCount++;
        if (config.isNullValue(rawValue)) {
            nullCount++;
            return;
        }
        String value = rawValue.trim();
        FieldType baseType = classifier.classify(value);
        typeCounts.merge(baseType, 1L, Long::sum);
        if (!distinctOverflowed) {
            distinctValues.add(value);
            if (distinctValues.size() > config.getEnumMaxDistinct()) {
                distinctOverflowed = true;
                distinctValues.clear();
            }
        }
    }

    String getName() {
        return name;
    }

    long getTotalCount() {
        return totalCount;
    }

    long getNullCount() {
        return nullCount;
    }

    Map<FieldType, Long> getTypeCounts() {
        return Collections.unmodifiableMap(typeCounts);
    }

    long countOf(FieldType type) {
        return typeCounts.getOrDefault(type, 0L);
    }

    boolean isDistinctOverflowed() {
        return distinctOverflowed;
    }

    Set<String> getDistinctValues() {
        return Collections.unmodifiableSet(distinctValues);
    }
}
