package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaEvolutionTest {

    private final SchemaEngine engine = new SchemaEngine();

    @Test
    void integerFieldSmoothlyUpgradesToDecimal() {
        engine.ingestBatch("b1", Rows.column("v", "1", "2", "3"));
        assertThat(engine.getFieldSchema("v").getType()).isEqualTo(FieldType.INTEGER);

        IngestReport report = engine.ingestBatch("b2", Rows.column("v", "1.5", "2.5"));
        assertThat(report.getDecision("v").status()).isEqualTo(FieldDecision.Status.EVOLVED);
        assertThat(report.isAccepted()).isTrue();
        assertThat(engine.getFieldSchema("v").getType()).isEqualTo(FieldType.DECIMAL);

        assertThat(engine.getEvolutionHistory()).hasSize(1);
        SchemaEvolution evolution = engine.getEvolutionHistory().get(0);
        assertThat(evolution.batchId()).isEqualTo("b2");
        assertThat(evolution.fieldName()).isEqualTo("v");
        assertThat(evolution.fromType()).isEqualTo(FieldType.INTEGER);
        assertThat(evolution.toType()).isEqualTo(FieldType.DECIMAL);
    }

    @Test
    void evolutionHistoryIsPreservedAcrossBatches() {
        engine.ingestBatch("b1", Rows.column("v", "1", "2"));
        engine.ingestBatch("b2", Rows.column("v", "1.5"));
        // rejected batch must not add history entries
        engine.ingestBatch("b3", Rows.column("v", "true", "false"));
        // further compatible batches must not add history entries either
        engine.ingestBatch("b4", Rows.column("v", "3.25", "4"));
        assertThat(engine.getEvolutionHistory()).hasSize(1);
        assertThat(engine.getFieldSchema("v").getType()).isEqualTo(FieldType.DECIMAL);
    }

    @Test
    void unknownFieldAdoptsTypeOfFirstNonNullBatch() {
        engine.ingestBatch("b1", Rows.column("v", null, null));
        assertThat(engine.getFieldSchema("v").getType()).isEqualTo(FieldType.UNKNOWN);

        IngestReport report = engine.ingestBatch("b2", Rows.column("v", "1", "2"));
        assertThat(report.getDecision("v").status()).isEqualTo(FieldDecision.Status.EVOLVED);
        assertThat(engine.getFieldSchema("v").getType()).isEqualTo(FieldType.INTEGER);
        assertThat(engine.getEvolutionHistory().get(0).fromType()).isEqualTo(FieldType.UNKNOWN);
        assertThat(engine.getEvolutionHistory().get(0).toType()).isEqualTo(FieldType.INTEGER);
    }

    @Test
    void enumValuesExtendAcrossBatchesWithinThreshold() {
        engine.ingestBatch("b1", repeated("status", 5, "A", "B"));
        IngestReport report = engine.ingestBatch("b2", repeated("status", 5, "B", "C"));
        assertThat(report.getDecision("status").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(engine.getFieldSchema("status").getType()).isEqualTo(FieldType.ENUM);
        assertThat(engine.getFieldSchema("status").getEnumValues())
                .containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void enumEvolvesToBaseTypeWhenCardinalityExceedsThreshold() {
        SchemaEngine smallEnumEngine = new SchemaEngine(InferenceConfig.builder()
                .enumMaxDistinct(3)
                .build());
        smallEnumEngine.ingestBatch("b1", repeated("status", 5, "A", "B"));
        assertThat(smallEnumEngine.getFieldSchema("status").getType()).isEqualTo(FieldType.ENUM);

        IngestReport report = smallEnumEngine.ingestBatch("b2", repeated("status", 5, "C", "D"));
        assertThat(report.getDecision("status").status()).isEqualTo(FieldDecision.Status.EVOLVED);
        assertThat(smallEnumEngine.getFieldSchema("status").getType()).isEqualTo(FieldType.STRING);
        assertThat(smallEnumEngine.getFieldSchema("status").getEnumValues()).isEmpty();

        SchemaEvolution evolution = smallEnumEngine.getEvolutionHistory().get(0);
        assertThat(evolution.fromType()).isEqualTo(FieldType.ENUM);
        assertThat(evolution.toType()).isEqualTo(FieldType.STRING);
    }

    @Test
    void enumFieldAcceptsCompatibleNonEnumBatch() {
        engine.ingestBatch("b1", repeated("status", 5, "A", "B"));
        IngestReport report = engine.ingestBatch("b2", repeated("status", 5, "A", "B"));
        assertThat(report.getDecision("status").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(engine.getFieldSchema("status").getType()).isEqualTo(FieldType.ENUM);
    }

    private static List<Map<String, String>> repeated(String field, int repeats, String... values) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < repeats; i++) {
            for (String value : values) {
                rows.add(Rows.row(field, value));
            }
        }
        return rows;
    }
}
