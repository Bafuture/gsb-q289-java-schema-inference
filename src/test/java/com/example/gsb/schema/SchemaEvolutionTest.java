package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaEvolutionTest {

    @Test
    void integerSmoothlyUpgradesToDecimalWithHistory() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.repeat("price", List.of("1", "2"), 10));
        assertThat(registry.currentSchema().orElseThrow().field("price").orElseThrow().type())
                .isEqualTo(FieldType.INTEGER);

        SchemaMergeResult result = registry.ingest(TestRows.repeat("price", List.of("3.14", "4.0"), 10));
        FieldProfile profile = result.schema().field("price").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.DECIMAL);
        assertThat(result.hasConflicts()).isFalse();
        assertThat(result.warnings()).anyMatch(w -> w.contains("INTEGER -> DECIMAL"));

        List<TypeEvolution> history = profile.typeHistory();
        assertThat(history).anyMatch(e ->
                e.fromType() == FieldType.UNKNOWN && e.toType() == FieldType.INTEGER && e.batchIndex() == 0);
        assertThat(history).anyMatch(e ->
                e.fromType() == FieldType.INTEGER && e.toType() == FieldType.DECIMAL && e.batchIndex() == 1);
    }

    @Test
    void upgradedFieldAcceptsIntegersAndDecimalsAfterwards() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.repeat("price", List.of("1", "2"), 8));
        registry.ingest(TestRows.repeat("price", List.of("3.14"), 8));
        SchemaMergeResult result = registry.ingest(TestRows.repeat("price", List.of("7", "8.8"), 8));
        FieldProfile profile = result.schema().field("price").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.DECIMAL);
        assertThat(result.hasConflicts()).isFalse();
        assertThat(profile.totalCount()).isEqualTo(24);
    }

    @Test
    void enumWidensToStringAndKeepsEnumSnapshot() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.repeat("color", List.of("RED", "GREEN", "BLUE"), 12));
        FieldProfile before = registry.currentSchema().orElseThrow().field("color").orElseThrow();
        assertThat(before.type()).isEqualTo(FieldType.ENUM);

        List<java.util.Map<String, String>> batch2 = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            batch2.add(TestRows.row("color", "free-text-" + i));
        }
        SchemaMergeResult result = registry.ingest(batch2);
        FieldProfile after = result.schema().field("color").orElseThrow();
        assertThat(after.type()).isEqualTo(FieldType.STRING);
        assertThat(after.typeHistory()).anyMatch(e ->
                e.fromType() == FieldType.ENUM && e.toType() == FieldType.STRING
                        && e.previousEnumValues().contains("RED"));
    }
}
