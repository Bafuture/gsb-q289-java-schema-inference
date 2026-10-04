package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaStatsTest {

    @Test
    void statsCoverFieldCountTypeDistributionConflictsAndEnums() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.rows(
                TestRows.row("i", "1", "d", "1.5", "b", "true", "t", "2024-01-15", "s", "hello", "e", "A"),
                TestRows.row("i", "2", "d", "2.5", "b", "false", "t", "2024-01-16", "s", "world", "e", "B"),
                TestRows.row("i", "3", "d", "3.5", "b", "true", "t", "2024-01-17", "s", "foo-bar", "e", "A"),
                TestRows.row("i", "4", "d", "4.5", "b", "false", "t", "2024-01-18", "s", "baz-qux", "e", "B")));
        // 制造一个冲突字段：先整数批次，再布尔批次
        registry.ingest(TestRows.rows(
                TestRows.row("i", "true"),
                TestRows.row("i", "false")));

        SchemaStats stats = registry.stats();
        assertThat(stats.fieldCount()).isEqualTo(6);
        assertThat(stats.countOf(FieldType.DECIMAL)).isEqualTo(1);
        assertThat(stats.countOf(FieldType.BOOLEAN)).isEqualTo(1);
        assertThat(stats.countOf(FieldType.DATETIME)).isEqualTo(1);
        assertThat(stats.countOf(FieldType.STRING)).isEqualTo(1);
        assertThat(stats.countOf(FieldType.ENUM)).isEqualTo(1);
        assertThat(stats.countOf(FieldType.CONFLICT)).isEqualTo(1);
        assertThat(stats.conflictFieldCount()).isEqualTo(1);
        assertThat(stats.enumFieldCount()).isEqualTo(1);
    }

    @Test
    void emptyRegistryHasZeroStats() {
        SchemaStats stats = new SchemaRegistry().stats();
        assertThat(stats.fieldCount()).isZero();
        assertThat(stats.typeDistribution()).isEmpty();
        assertThat(stats.conflictFieldCount()).isZero();
        assertThat(stats.enumFieldCount()).isZero();
    }

    @Test
    void schemaStatsOfSingleBatch() {
        Schema schema = new SchemaInferrer().inferBatch(TestRows.rows(
                TestRows.row("a", "1", "b", "x"),
                TestRows.row("a", "2", "b", "y")));
        SchemaStats stats = SchemaStats.of(schema);
        assertThat(stats.fieldCount()).isEqualTo(2);
        assertThat(stats.countOf(FieldType.INTEGER)).isEqualTo(1);
        assertThat(stats.conflictFieldCount()).isZero();
    }
}
