package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NullHandlingTest {

    @Test
    void nullsAndMissingDoNotAffectTypeInference() {
        Schema schema = new SchemaInferrer().inferBatch(TestRows.rows(
                TestRows.row("a", "1", "b", "x"),
                TestRows.row("a", null, "b", "y"),
                TestRows.row("a", "3"),
                TestRows.row("a", "  ", "b", "z")));
        assertThat(schema.field("a").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        FieldProfile a = schema.field("a").orElseThrow();
        assertThat(a.nullCount()).isEqualTo(2);
        assertThat(a.totalCount()).isEqualTo(4);
    }

    @Test
    void allNullFieldIsUnknownWithWarning() {
        List<String> warnings = new ArrayList<>();
        Schema schema = new SchemaInferrer().inferBatch(TestRows.rows(
                TestRows.row("v", null),
                TestRows.row("v", " ")), 0, warnings);
        FieldProfile profile = schema.field("v").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.UNKNOWN);
        assertThat(profile.confidence()).isEqualTo(0.0);
        assertThat(warnings).anyMatch(w -> w.contains("无法推断类型"));
    }

    @Test
    void highNullRatioProducesWarning() {
        List<String> warnings = new ArrayList<>();
        List<java.util.Map<String, String>> rows = new ArrayList<>();
        rows.add(TestRows.row("v", "42"));
        for (int i = 0; i < 9; i++) {
            rows.add(TestRows.row("v", null));
        }
        Schema schema = new SchemaInferrer().inferBatch(rows, 0, warnings);
        FieldProfile profile = schema.field("v").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.INTEGER);
        assertThat(profile.nullRatio()).isCloseTo(0.9, within(1e-9));
        assertThat(warnings).anyMatch(w -> w.contains("空值占比"));
    }

    @Test
    void nullRatioThresholdIsConfigurable() {
        List<String> warnings = new ArrayList<>();
        InferenceConfig config = InferenceConfig.DEFAULT.withHighNullRatioThreshold(0.9);
        List<java.util.Map<String, String>> rows = new ArrayList<>();
        rows.add(TestRows.row("v", "1"));
        rows.add(TestRows.row("v", null));
        rows.add(TestRows.row("v", null));
        new SchemaInferrer(config).inferBatch(rows, 0, warnings);
        assertThat(warnings).noneMatch(w -> w.contains("空值占比"));
    }

    @Test
    void unknownFieldGetsTypeFromLaterBatch() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.ingest(TestRows.rows(TestRows.row("v", null), TestRows.row("v", null)));
        assertThat(registry.currentSchema().orElseThrow().field("v").orElseThrow().type())
                .isEqualTo(FieldType.UNKNOWN);
        SchemaMergeResult result = registry.ingest(TestRows.rows(
                TestRows.row("v", "1"), TestRows.row("v", "2")));
        assertThat(result.schema().field("v").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        assertThat(result.hasConflicts()).isFalse();
    }
}
