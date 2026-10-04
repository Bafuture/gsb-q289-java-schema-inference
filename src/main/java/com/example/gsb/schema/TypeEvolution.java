package com.example.gsb.schema;

import java.util.Set;

/**
 * 一次类型演进记录：字段从 fromType 平滑升级到 toType（如 INTEGER -> DECIMAL），
 * 并保留演进前的枚举取值快照，便于追溯历史。
 */
public record TypeEvolution(FieldType fromType,
                            FieldType toType,
                            int batchIndex,
                            Set<String> previousEnumValues) {

    public TypeEvolution {
        previousEnumValues = previousEnumValues == null ? Set.of() : Set.copyOf(previousEnumValues);
    }

    public static TypeEvolution of(FieldType from, FieldType to, int batchIndex) {
        return new TypeEvolution(from, to, batchIndex, Set.of());
    }

    public static TypeEvolution ofEnum(FieldType from, FieldType to, int batchIndex, Set<String> previousValues) {
        return new TypeEvolution(from, to, batchIndex, previousValues);
    }

    @Override
    public String toString() {
        return fromType + " -> " + toType + " @batch " + batchIndex;
    }
}
