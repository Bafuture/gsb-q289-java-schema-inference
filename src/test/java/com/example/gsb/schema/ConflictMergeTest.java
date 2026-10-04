package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConflictMergeTest {

    private final SchemaInferrer inferrer = new SchemaInferrer();

    @Test
    void integerAndDecimalMergeToDecimal() {
        Schema schema = inferrer.inferBatch(TestRows.rows(
                TestRows.row("v", "1"),
                TestRows.row("v", "2"),
                TestRows.row("v", "3.14"),
                TestRows.row("v", "2e2")));
        FieldProfile profile = schema.field("v").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.DECIMAL);
    }

    @Test
    void incompatibleTypesProduceConflict() {
        List<String> warnings = new ArrayList<>();
        Schema schema = inferrer.inferBatch(TestRows.rows(
                TestRows.row("v", "1"),
                TestRows.row("v", "2"),
                TestRows.row("v", "alice"),
                TestRows.row("v", "2024-01-15")), 0, warnings);
        FieldProfile profile = schema.field("v").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.CONFLICT);
        assertThat(warnings).anyMatch(w -> w.contains("无法合并"));
    }

    @Test
    void crossBatchBooleanVsDateTimeCannotMerge() {
        SchemaRegistry registry = new SchemaRegistry(InferenceConfig.DEFAULT, MergePolicy.EXPAND);
        SchemaMergeResult first = registry.ingest(TestRows.rows(
                TestRows.row("when", "true"), TestRows.row("when", "false")));
        assertThat(first.schema().field("when").orElseThrow().type()).isEqualTo(FieldType.BOOLEAN);

        SchemaMergeResult second = registry.ingest(TestRows.rows(
                TestRows.row("when", "2024-01-15"), TestRows.row("when", "2024-02-01")));
        assertThat(second.hasConflicts()).isTrue();
        assertThat(second.conflictFields()).contains("when");
        assertThat(second.schema().field("when").orElseThrow().type()).isEqualTo(FieldType.CONFLICT);
        assertThat(second.warnings()).anyMatch(w -> w.contains("类型冲突"));
    }

    @Test
    void strictPolicyRejectsConflictingBatchWithoutChangingType() {
        SchemaRegistry registry = new SchemaRegistry(InferenceConfig.DEFAULT, MergePolicy.STRICT);
        registry.ingest(TestRows.rows(TestRows.row("v", "1"), TestRows.row("v", "2")));
        SchemaMergeResult result = registry.ingest(TestRows.rows(
                TestRows.row("v", "hello"), TestRows.row("v", "world-world")));
        assertThat(result.hasRejections()).isTrue();
        assertThat(result.rejectedFields()).contains("v");
        assertThat(registry.currentSchema().orElseThrow().field("v").orElseThrow().type())
                .isEqualTo(FieldType.INTEGER);
    }
}
