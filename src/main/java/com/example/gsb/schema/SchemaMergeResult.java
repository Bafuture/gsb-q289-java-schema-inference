package com.example.gsb.schema;

import java.util.List;
import java.util.Set;

/**
 * 一次跨批次合并的结果：合并后的模式、冲突字段、被 STRICT 策略拒绝的字段、警告信息。
 */
public record SchemaMergeResult(Schema schema,
                                Set<String> conflictFields,
                                Set<String> rejectedFields,
                                List<String> warnings) {

    public SchemaMergeResult {
        conflictFields = Set.copyOf(conflictFields);
        rejectedFields = Set.copyOf(rejectedFields);
        warnings = List.copyOf(warnings);
    }

    public boolean hasConflicts() {
        return !conflictFields.isEmpty();
    }

    public boolean hasRejections() {
        return !rejectedFields.isEmpty();
    }
}
