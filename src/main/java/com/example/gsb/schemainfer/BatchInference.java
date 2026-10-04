package com.example.gsb.schemainfer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inference results for all fields observed in a single batch. */
public final class BatchInference {

    private final Map<String, FieldInference> fields;

    BatchInference(Map<String, FieldInference> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, FieldInference> getFields() {
        return fields;
    }

    public FieldInference getField(String name) {
        return fields.get(name);
    }

    public List<FieldInference> getConflictingFields() {
        return fields.values().stream().filter(FieldInference::hasConflict).toList();
    }

    public List<FieldInference> getEnumFields() {
        return fields.values().stream().filter(FieldInference::isEnum).toList();
    }
}
