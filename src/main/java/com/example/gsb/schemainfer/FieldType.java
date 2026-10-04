package com.example.gsb.schemainfer;

/**
 * Field types that the inference component can recognize.
 *
 * <p>{@link #INTEGER}, {@link #DECIMAL}, {@link #BOOLEAN}, {@link #DATETIME}
 * and {@link #STRING} are the atomic value (base) types. {@link #ENUM} is a
 * refinement of a base type (string or integer) with low cardinality and high
 * repetition. {@link #UNKNOWN} is used for fields that only ever produced
 * null/empty samples and therefore carry no type evidence yet.
 */
public enum FieldType {
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATETIME,
    STRING,
    ENUM,
    UNKNOWN;

    public boolean isNumeric() {
        return this == INTEGER || this == DECIMAL;
    }
}
