package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValueClassifierTest {

    private final ValueClassifier classifier = new ValueClassifier();

    @Test
    void classifiesIntegers() {
        assertThat(classifier.classify("42")).isEqualTo(FieldType.INTEGER);
        assertThat(classifier.classify("-7")).isEqualTo(FieldType.INTEGER);
        assertThat(classifier.classify("+1000")).isEqualTo(FieldType.INTEGER);
        // beyond long range is not an integer
        assertThat(classifier.classify("9999999999999999999999")).isEqualTo(FieldType.DECIMAL);
    }

    @Test
    void classifiesDecimals() {
        assertThat(classifier.classify("3.14")).isEqualTo(FieldType.DECIMAL);
        assertThat(classifier.classify("-0.5")).isEqualTo(FieldType.DECIMAL);
        assertThat(classifier.classify("1e5")).isEqualTo(FieldType.DECIMAL);
        assertThat(classifier.classify("1.")).isEqualTo(FieldType.DECIMAL);
        assertThat(classifier.classify(".5")).isEqualTo(FieldType.DECIMAL);
    }

    @Test
    void classifiesBooleansCaseInsensitively() {
        assertThat(classifier.classify("true")).isEqualTo(FieldType.BOOLEAN);
        assertThat(classifier.classify("FALSE")).isEqualTo(FieldType.BOOLEAN);
        assertThat(classifier.classify("Yes")).isEqualTo(FieldType.BOOLEAN);
        assertThat(classifier.classify("no")).isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void classifiesDateTimes() {
        assertThat(classifier.classify("2026-10-05")).isEqualTo(FieldType.DATETIME);
        assertThat(classifier.classify("2026-10-05T10:30:00")).isEqualTo(FieldType.DATETIME);
        assertThat(classifier.classify("2026-10-05T10:30:00Z")).isEqualTo(FieldType.DATETIME);
        assertThat(classifier.classify("2026-10-05 08:00:00")).isEqualTo(FieldType.DATETIME);
        assertThat(classifier.classify("2026/10/05 08:00:00")).isEqualTo(FieldType.DATETIME);
        assertThat(classifier.classify("2026/10/05")).isEqualTo(FieldType.DATETIME);
    }

    @Test
    void fallsBackToString() {
        assertThat(classifier.classify("hello")).isEqualTo(FieldType.STRING);
        assertThat(classifier.classify("123abc")).isEqualTo(FieldType.STRING);
        assertThat(classifier.classify("foo bar")).isEqualTo(FieldType.STRING);
        assertThat(classifier.classify("10:30")).isEqualTo(FieldType.STRING);
    }
}
