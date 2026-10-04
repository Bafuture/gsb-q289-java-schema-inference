package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnumDetectionTest {

    private final SchemaEngine engine = new SchemaEngine();

    @Test
    void detectsLowCardinalityHighRepetitionStringFieldAsEnum() {
        FieldInference field = engine.inferBatch(repeated("status", 10, "ACTIVE", "INACTIVE"))
                .getField("status");
        assertThat(field.getType()).isEqualTo(FieldType.ENUM);
        assertThat(field.getBaseType()).isEqualTo(FieldType.STRING);
        assertThat(field.getEnumValues()).containsExactlyInAnyOrder("ACTIVE", "INACTIVE");
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void detectsIntegerEnum() {
        FieldInference field = engine.inferBatch(repeated("level", 10, "1", "2", "3"))
                .getField("level");
        assertThat(field.getType()).isEqualTo(FieldType.ENUM);
        assertThat(field.getBaseType()).isEqualTo(FieldType.INTEGER);
        assertThat(field.getEnumValues()).containsExactlyInAnyOrder("1", "2", "3");
    }

    @Test
    void distinctCountAboveThresholdStaysString() {
        // 30 distinct values, each repeated twice -> repetition is high but
        // cardinality exceeds the default threshold of 20.
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(Rows.row("code", "C" + i));
            rows.add(Rows.row("code", "C" + i));
        }
        FieldInference field = engine.inferBatch(rows).getField("code");
        assertThat(field.getType()).isEqualTo(FieldType.STRING);
        assertThat(field.isEnum()).isFalse();
    }

    @Test
    void lowRepetitionStaysString() {
        // 10 distinct values in 11 rows -> repetition ratio ~0.09 < 0.5
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(Rows.row("v", "V" + i));
        }
        rows.add(Rows.row("v", "V0"));
        FieldInference field = engine.inferBatch(rows).getField("v");
        assertThat(field.getType()).isEqualTo(FieldType.STRING);
    }

    @Test
    void enumThresholdsAreConfigurable() {
        SchemaEngine strictEngine = new SchemaEngine(InferenceConfig.builder()
                .enumMaxDistinct(3)
                .build());
        assertThat(strictEngine.inferBatch(repeated("v", 10, "A", "B", "C")).getField("v").getType())
                .isEqualTo(FieldType.ENUM);
        assertThat(strictEngine.inferBatch(repeated("v", 10, "A", "B", "C", "D")).getField("v").getType())
                .isEqualTo(FieldType.STRING);

        SchemaEngine demandingEngine = new SchemaEngine(InferenceConfig.builder()
                .enumMinRepetitionRatio(0.95)
                .build());
        // 2 distinct in 20 rows -> repetition 0.9, below the demanding 0.95
        assertThat(demandingEngine.inferBatch(repeated("v", 10, "A", "B")).getField("v").getType())
                .isEqualTo(FieldType.STRING);
    }

    @Test
    void tinySampleIsNotEnoughForEnum() {
        // fewer non-null samples than enumMinSamples (default 5)
        FieldInference field = engine.inferBatch(Rows.column("v", "A", "A", "B")).getField("v");
        assertThat(field.getType()).isEqualTo(FieldType.STRING);
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
