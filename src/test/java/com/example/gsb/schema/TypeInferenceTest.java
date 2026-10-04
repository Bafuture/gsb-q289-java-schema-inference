package com.example.gsb.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class TypeInferenceTest {

    private final SchemaInferrer inferrer = new SchemaInferrer();

    @Test
    void infersInteger() {
        Schema schema = inferrer.inferBatch(TestRows.repeat("age", List.of("1", "25", "300", "-7"), 8));
        FieldProfile profile = schema.field("age").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.INTEGER);
        assertThat(profile.confidence()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void infersDecimal() {
        Schema schema = inferrer.inferBatch(TestRows.repeat("price", List.of("1.5", "0.99", "-3.14", "2e3"), 8));
        FieldProfile profile = schema.field("price").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.DECIMAL);
    }

    @Test
    void infersBoolean() {
        Schema schema = inferrer.inferBatch(TestRows.repeat("active", List.of("true", "FALSE", "True"), 9));
        FieldProfile profile = schema.field("active").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void infersDateTime() {
        Schema schema = inferrer.inferBatch(TestRows.rows(
                TestRows.row("created", "2024-01-15"),
                TestRows.row("created", "2024-01-16 10:30:00"),
                TestRows.row("created", "2024-01-17T08:00:00"),
                TestRows.row("created", "2024-01-18T08:00:00+08:00")));
        FieldProfile profile = schema.field("created").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.DATETIME);
    }

    @Test
    void infersStringForFreeText() {
        Schema schema = inferrer.inferBatch(TestRows.repeat("name",
                List.of("alice-wonder", "bob the builder", "张三", "x@y.com", "a", "b", "c", "d", "e", "f",
                        "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r"), 22));
        FieldProfile profile = schema.field("name").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void integerTakesPrecedenceOverDateTimeLikeDigits() {
        // 纯数字串应判为整数而不是日期
        assertThat(ValueTypeClassifier.classify("20240115")).isEqualTo(FieldType.INTEGER);
    }

    @Test
    void confidenceDropsWithNullRatio() {
        Schema schema = inferrer.inferBatch(TestRows.rows(
                TestRows.row("v", "1"),
                TestRows.row("v", "2"),
                TestRows.row("v", null),
                TestRows.row("v", " ")));
        FieldProfile profile = schema.field("v").orElseThrow();
        assertThat(profile.type()).isEqualTo(FieldType.INTEGER);
        assertThat(profile.confidence()).isCloseTo(0.5, within(1e-9));
    }
}
