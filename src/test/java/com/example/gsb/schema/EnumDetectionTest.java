package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnumDetectionTest {

    @Test
    void detectsEnumWhenFewDistinctAndHighRepetition() {
        List<java.util.Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(TestRows.row("status", i % 3 == 0 ? "PENDING" : i % 3 == 1 ? "ACTIVE" : "CLOSED"));
        }
        Schema schema = new SchemaInferrer().inferBatch(rows);
        FieldProfile profile = schema.field("status").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.ENUM);
        assertThat(profile.enumValues()).containsExactlyInAnyOrder("PENDING", "ACTIVE", "CLOSED");
    }

    @Test
    void distinctAboveThresholdFallsBackToString() {
        List<java.util.Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(TestRows.row("code", "value-" + i));
        }
        Schema schema = new SchemaInferrer().inferBatch(rows);
        assertThat(schema.field("code").orElseThrow().type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void fewDistinctButNoRepetitionIsNotEnum() {
        InferenceConfig config = InferenceConfig.DEFAULT
                .withEnumMaxDistinctValues(10)
                .withEnumMinRepetitionRate(0.5);
        Schema schema = new SchemaInferrer(config).inferBatch(TestRows.rows(
                TestRows.row("c", "a"), TestRows.row("c", "b"), TestRows.row("c", "c")));
        assertThat(schema.field("c").orElseThrow().type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void configurableThresholdAllowsLargerEnums() {
        List<java.util.Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(TestRows.row("code", "code-" + (i % 8)));
        }
        InferenceConfig config = InferenceConfig.DEFAULT.withEnumMaxDistinctValues(10);
        Schema schema = new SchemaInferrer(config).inferBatch(rows);
        assertThat(schema.field("code").orElseThrow().type()).isEqualTo(FieldType.ENUM);
    }

    @Test
    void enumValuesExtendAcrossBatches() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.repeat("status", List.of("A", "B"), 10));
        SchemaMergeResult result = registry.ingest(TestRows.repeat("status", List.of("A", "C"), 10));
        FieldProfile profile = result.schema().field("status").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.ENUM);
        assertThat(profile.enumValues()).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(result.hasConflicts()).isFalse();
    }
}
