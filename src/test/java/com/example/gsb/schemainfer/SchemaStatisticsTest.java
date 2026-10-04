package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaStatisticsTest {

    @Test
    void reportsFieldCountTypeDistributionAndEnumCount() {
        SchemaEngine engine = new SchemaEngine();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(Rows.row(
                    "age", String.valueOf(i),
                    "price", "1.5",
                    "active", i % 2 == 0 ? "true" : "false",
                    "created", "2026-01-01",
                    "name", "user-" + i + "-unique-value",
                    "status", i % 2 == 0 ? "OPEN" : "CLOSED"));
        }
        engine.ingestBatch("b1", rows);

        SchemaStatistics stats = engine.getStatistics();
        assertThat(stats.fieldCount()).isEqualTo(6);
        assertThat(stats.typeDistribution()).containsEntry(FieldType.INTEGER, 1L);
        assertThat(stats.typeDistribution()).containsEntry(FieldType.DECIMAL, 1L);
        assertThat(stats.typeDistribution()).containsEntry(FieldType.BOOLEAN, 1L);
        assertThat(stats.typeDistribution()).containsEntry(FieldType.DATETIME, 1L);
        assertThat(stats.typeDistribution()).containsEntry(FieldType.STRING, 1L);
        assertThat(stats.typeDistribution()).containsEntry(FieldType.ENUM, 1L);
        assertThat(stats.enumFieldCount()).isEqualTo(1);
        assertThat(stats.conflictFieldCount()).isZero();
    }

    @Test
    void countsFieldsThatProduceConflicts() {
        SchemaEngine engine = new SchemaEngine();
        engine.ingestBatch("b1", List.of(
                Rows.row("clean", "1", "messy", "1"),
                Rows.row("clean", "2", "messy", "true")));
        SchemaStatistics stats = engine.getStatistics();
        assertThat(stats.conflictFieldCount()).isEqualTo(1);
        assertThat(engine.getConflictedFields()).containsExactly("messy");
    }

    @Test
    void countsRejectedFieldsAsConflicted() {
        SchemaEngine engine = new SchemaEngine();
        engine.ingestBatch("b1", Rows.column("age", "1", "2", "3", "4", "5"));
        engine.ingestBatch("b2", Rows.column("age", "x", "y", "z", "w", "q"));
        assertThat(engine.getStatistics().conflictFieldCount()).isEqualTo(1);
        // rejected batch must not corrupt established stats
        assertThat(engine.getFieldSchema("age").getType()).isEqualTo(FieldType.INTEGER);
        assertThat(engine.getFieldSchema("age").getTotalCount()).isEqualTo(5);
    }
}
