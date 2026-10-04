package com.example.gsb.schemainfer;

import java.time.Instant;

/** One recorded schema change, e.g. a field widening from INTEGER to DECIMAL. */
public record SchemaEvolution(String batchId, String fieldName, FieldType fromType,
                              FieldType toType, String reason, Instant timestamp) {
}
