package com.example.gsb.schema;

import java.util.EnumMap;
import java.util.Map;

/**
 * 模式统计：字段数、各类型分布、冲突字段数、枚举字段数。
 */
public record SchemaStats(int fieldCount,
                          Map<FieldType, Integer> typeDistribution,
                          int conflictFieldCount,
                          int enumFieldCount) {

    public SchemaStats {
        typeDistribution = Map.copyOf(typeDistribution);
    }

    public static SchemaStats of(Schema schema) {
        Map<FieldType, Integer> distribution = new EnumMap<>(FieldType.class);
        int conflicts = 0;
        int enums = 0;
        for (FieldProfile profile : schema.fields().values()) {
            distribution.merge(profile.type(), 1, Integer::sum);
            if (profile.isConflict()) {
                conflicts++;
            }
            if (profile.isEnum()) {
                enums++;
            }
        }
        return new SchemaStats(schema.fieldCount(), distribution, conflicts, enums);
    }

    public int countOf(FieldType type) {
        return typeDistribution.getOrDefault(type, 0);
    }
}
