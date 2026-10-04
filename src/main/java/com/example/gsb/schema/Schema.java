package com.example.gsb.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 一份完整的模式：字段名 -> 字段模式。
 */
public final class Schema {

    private final Map<String, FieldProfile> fields;

    public Schema(Map<String, FieldProfile> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, FieldProfile> fields() {
        return fields;
    }

    public Optional<FieldProfile> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public int fieldCount() {
        return fields.size();
    }
}
