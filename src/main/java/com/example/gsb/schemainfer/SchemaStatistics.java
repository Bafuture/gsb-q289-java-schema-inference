package com.example.gsb.schemainfer;

import java.util.Map;

/**
 * Aggregate statistics over the established schema.
 *
 * @param fieldCount        number of known fields
 * @param typeDistribution  count of fields per resolved {@link FieldType}
 * @param conflictFieldCount number of distinct fields that ever produced an
 *                          unresolvable type conflict (including rejected
 *                          batches) across ingested batches
 * @param enumFieldCount    number of fields currently resolved as {@link FieldType#ENUM}
 */
public record SchemaStatistics(int fieldCount, Map<FieldType, Long> typeDistribution,
                               int conflictFieldCount, int enumFieldCount) {
}
