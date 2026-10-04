package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrossBatchConsistencyTest {

    @Test
    void consistentTypeAcrossBatchesStaysStable() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.repeat("age", java.util.List.of("1", "2"), 10));
        SchemaMergeResult result = registry.ingest(TestRows.repeat("age", java.util.List.of("3", "4"), 10));
        FieldProfile profile = result.schema().field("age").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.INTEGER);
        assertThat(result.hasConflicts()).isFalse();
    }

    @Test
    void newFieldsAreAddedWithoutAffectingExisting() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.repeat("a", java.util.List.of("1"), 4));
        SchemaMergeResult result = registry.ingest(TestRows.rows(
                TestRows.row("a", "2", "b", "hello"),
                TestRows.row("a", "3", "b", "world-world")));
        assertThat(result.schema().fieldCount()).isEqualTo(2);
        assertThat(result.schema().field("a").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        assertThat(result.schema().field("b").orElseThrow().type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void expandPolicyNeverSilentlyChangesTypeForIncompatibleIncoming() {
        SchemaRegistry registry = new SchemaRegistry(InferenceConfig.DEFAULT, MergePolicy.EXPAND);
        registry.ingest(TestRows.repeat("v", java.util.List.of("true"), 4));
        SchemaMergeResult result = registry.ingest(TestRows.repeat("v", java.util.List.of("1"), 4));
        // 不允许把 BOOLEAN 静默改成 INTEGER：显式冲突
        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.schema().field("v").orElseThrow().type()).isEqualTo(FieldType.CONFLICT);
        assertThat(result.warnings()).anyMatch(w -> w.contains("BOOLEAN") && w.contains("INTEGER"));
    }

    @Test
    void strictPolicyRejectsIntegerToDecimalUpgrade() {
        SchemaRegistry registry = new SchemaRegistry(InferenceConfig.DEFAULT, MergePolicy.STRICT);
        registry.ingest(TestRows.repeat("v", java.util.List.of("1"), 6));
        SchemaMergeResult result = registry.ingest(TestRows.repeat("v", java.util.List.of("1.5"), 6));
        assertThat(result.hasRejections()).isTrue();
        assertThat(result.schema().field("v").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        assertThat(result.warnings()).anyMatch(w -> w.contains("拒绝"));
    }

    @Test
    void rejectedBatchDoesNotCorruptCountsOnNextBatch() {
        SchemaRegistry registry = new SchemaRegistry(InferenceConfig.DEFAULT, MergePolicy.STRICT);
        registry.ingest(TestRows.repeat("v", java.util.List.of("1"), 6));
        registry.ingest(TestRows.repeat("v", java.util.List.of("1.5"), 6));
        SchemaMergeResult result = registry.ingest(TestRows.repeat("v", java.util.List.of("7"), 6));
        FieldProfile profile = result.schema().field("v").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.INTEGER);
        assertThat(profile.totalCount()).isEqualTo(18);
    }
}
