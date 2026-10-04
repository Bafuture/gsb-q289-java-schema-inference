package com.example.gsb.schemainfer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Established schema of a single field, accumulated across accepted/evolved
 * batches. Mutation methods are package-private: only {@link SchemaEngine}
 * reconciliation may change a schema.
 */
public final class FieldSchema {

    private final String name;
    private FieldType type;
    private FieldType baseType;
    private final Set<String> enumValues = new LinkedHashSet<>();
    private long totalCount;
    private long nullCount;

    private FieldSchema(String name, FieldType type, FieldType baseType, long totalCount,
                        long nullCount, Set<String> enumValues) {
        this.name = name;
        this.type = type;
        this.baseType = baseType;
        this.totalCount = totalCount;
        this.nullCount = nullCount;
        this.enumValues.addAll(enumValues);
    }

    static FieldSchema fromInference(FieldInference inference) {
        return new FieldSchema(inference.getName(), inference.getType(), inference.getBaseType(),
                inference.getTotalCount(), inference.getNullCount(), inference.getEnumValues());
    }

    /** Adopt type/enum values from an inference without dropping prior counts. */
    void adopt(FieldInference inference) {
        this.type = inference.getType();
        this.baseType = inference.getBaseType();
        this.enumValues.clear();
        this.enumValues.addAll(inference.getEnumValues());
        addCounts(inference.getTotalCount(), inference.getNullCount());
    }

    void addCounts(long total, long nulls) {
        this.totalCount += total;
        this.nullCount += nulls;
    }

    void changeType(FieldType newType, FieldType newBaseType) {
        this.type = newType;
        this.baseType = newBaseType;
        if (newType != FieldType.ENUM) {
            this.enumValues.clear();
        }
    }

    void addEnumValues(Set<String> values) {
        this.enumValues.addAll(values);
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

    public boolean isEnum() {
        return type == FieldType.ENUM;
    }

    public Set<String> getEnumValues() {
        return Collections.unmodifiableSet(enumValues);
    }

    public long getTotalCount() {
        return totalCount;
    }

    public long getNullCount() {
        return nullCount;
    }

    public double getNullRatio() {
        return totalCount == 0 ? 0.0 : (double) nullCount / totalCount;
    }
}
