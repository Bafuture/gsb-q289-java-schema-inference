package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NullHandlingTest {

    private final SchemaEngine engine = new SchemaEngine();

    @Test
    void nullAndEmptyAndNullTokensDoNotAffectInference() {
        FieldInference field = engine.inferBatch(Rows.column("age",
                "1", "2", null, "", "  ", "null", "NULL", "N/A")).getField("age");
        assertThat(field.getType()).isEqualTo(FieldType.INTEGER);
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
        assertThat(field.getNonNullCount()).isEqualTo(2);
        assertThat(field.getNullCount()).isEqualTo(6);
        assertThat(field.getNullRatio()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void missingKeysCountAsNullValues() {
        List<Map<String, String>> rows = List.of(
                Rows.row("a", "1"),
                Rows.row("a", "2", "b", "x"),
                Rows.row("b", "y"));
        BatchInference inference = engine.inferBatch(rows);
        assertThat(inference.getField("a").getNullCount()).isEqualTo(1);
        assertThat(inference.getField("b").getNullCount()).isEqualTo(1);
    }

    @Test
    void highNullRatioFlagsMostlyNullButStillTypesTheField() {
        FieldInference field = engine.inferBatch(Rows.column("v",
                "1", null, null, null, null, null, null, null, null, null)).getField("v");
        assertThat(field.getType()).isEqualTo(FieldType.INTEGER);
        assertThat(field.isMostlyNull()).isTrue();
        assertThat(field.getNullRatio()).isCloseTo(0.9, within(1e-9));
    }

    @Test
    void allNullFieldIsUnknown() {
        FieldInference field = engine.inferBatch(
                Rows.column("v", null, "", "null", "N/A")).getField("v");
        assertThat(field.getType()).isEqualTo(FieldType.UNKNOWN);
        assertThat(field.getConfidence()).isEqualTo(0.0);
        assertThat(field.hasConflict()).isFalse();
    }

    @Test
    void allNullBatchDoesNotChangeEstablishedType() {
        engine.ingestBatch("b1", Rows.column("age", "1", "2", "3"));
        IngestReport report = engine.ingestBatch("b2", Rows.column("age", null, null));
        assertThat(report.getDecision("age").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(engine.getFieldSchema("age").getType()).isEqualTo(FieldType.INTEGER);
        assertThat(report.getInference().getField("age").getType()).isEqualTo(FieldType.UNKNOWN);
    }
}
