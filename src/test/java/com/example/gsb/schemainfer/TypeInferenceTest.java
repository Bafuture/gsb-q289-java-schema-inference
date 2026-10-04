package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeInferenceTest {

    private final SchemaEngine engine = new SchemaEngine();

    @Test
    void infersIntegerFieldWithFullConfidence() {
        FieldInference field = singleField(Rows.column("age", "1", "2", "-3", "42"), "age");
        assertThat(field.getType()).isEqualTo(FieldType.INTEGER);
        assertThat(field.getBaseType()).isEqualTo(FieldType.INTEGER);
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
        assertThat(field.hasConflict()).isFalse();
    }

    @Test
    void infersDecimalField() {
        FieldInference field = singleField(Rows.column("price", "1.5", "2.0", "3.25"), "price");
        assertThat(field.getType()).isEqualTo(FieldType.DECIMAL);
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void mergesIntegerAndDecimalIntoDecimalByPriority() {
        FieldInference field = singleField(Rows.column("v", "1", "2", "3.5", "4"), "v");
        assertThat(field.getType()).isEqualTo(FieldType.DECIMAL);
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
        assertThat(field.hasConflict()).isFalse();
    }

    @Test
    void infersBooleanField() {
        FieldInference field = singleField(
                Rows.column("active", "true", "false", "TRUE", "yes", "no"), "active");
        assertThat(field.getType()).isEqualTo(FieldType.BOOLEAN);
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void infersDateTimeField() {
        FieldInference field = singleField(Rows.column("created",
                "2026-01-01", "2026-01-02T10:30:00", "2026-01-03 08:00:00"), "created");
        assertThat(field.getType()).isEqualTo(FieldType.DATETIME);
        assertThat(field.getConfidence()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void infersStringField() {
        FieldInference field = singleField(
                Rows.column("name", "alice", "bob", "charlie"), "name");
        assertThat(field.getType()).isEqualTo(FieldType.STRING);
    }

    @Test
    void reportsIncompatibleTypesAsConflictWithReducedConfidence() {
        // 2 numeric values vs 1 boolean -> numeric wins, boolean is a conflict
        FieldInference field = singleField(Rows.column("v", "1", "2", "true"), "v");
        assertThat(field.getType()).isEqualTo(FieldType.INTEGER);
        assertThat(field.hasConflict()).isTrue();
        assertThat(field.getConflictingTypes()).containsExactly(FieldType.BOOLEAN);
        assertThat(field.getConfidence()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void conflictAppearsOnTheBatchResult() {
        List<Map<String, String>> rows = List.of(
                Rows.row("a", "1", "b", "true"),
                Rows.row("a", "x", "b", "false"));
        BatchInference inference = engine.inferBatch(rows);
        assertThat(inference.getConflictingFields()).hasSize(1);
        assertThat(inference.getConflictingFields().get(0).getName()).isEqualTo("a");
    }

    private FieldInference singleField(List<Map<String, String>> rows, String name) {
        return engine.inferBatch(rows).getField(name);
    }
}
