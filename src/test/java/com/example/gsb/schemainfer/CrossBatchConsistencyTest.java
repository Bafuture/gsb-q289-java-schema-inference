package com.example.gsb.schemainfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossBatchConsistencyTest {

    private final SchemaEngine engine = new SchemaEngine();

    @Test
    void consistentBatchesAreAccepted() {
        IngestReport first = engine.ingestBatch("b1", Rows.column("age", "1", "2", "3"));
        IngestReport second = engine.ingestBatch("b2", Rows.column("age", "4", "5", "6"));
        assertThat(first.getDecision("age").status()).isEqualTo(FieldDecision.Status.ADDED);
        assertThat(second.getDecision("age").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(second.isAccepted()).isTrue();
        assertThat(engine.getFieldSchema("age").getType()).isEqualTo(FieldType.INTEGER);
        assertThat(engine.getFieldSchema("age").getTotalCount()).isEqualTo(6);
    }

    @Test
    void previouslyUnknownFieldIsAddedOnLaterBatches() {
        IngestReport first = engine.ingestBatch("b1",
                List.of(Rows.row("age", "1"), Rows.row("age", "2", "name", "alice")));
        assertThat(first.getDecision("age").status()).isEqualTo(FieldDecision.Status.ADDED);
        assertThat(first.getDecision("name").status()).isEqualTo(FieldDecision.Status.ADDED);
    }

    @Test
    void incompatibleBatchIsRejectedAndSchemaIsUnchanged() {
        engine.ingestBatch("b1", Rows.column("age", "1", "2", "3"));
        IngestReport report = engine.ingestBatch("b2",
                Rows.column("age", "alice", "bob", "charlie"));
        assertThat(report.isAccepted()).isFalse();
        assertThat(report.getDecision("age").status()).isEqualTo(FieldDecision.Status.REJECTED);
        assertThat(engine.getFieldSchema("age").getType()).isEqualTo(FieldType.INTEGER);
        assertThat(engine.getEvolutionHistory()).isEmpty();
    }

    @Test
    void booleanValuesIntoIntegerFieldAreRejected() {
        engine.ingestBatch("b1", Rows.column("flag", "1", "2", "3"));
        IngestReport report = engine.ingestBatch("b2", Rows.column("flag", "true", "false"));
        assertThat(report.getDecision("flag").status()).isEqualTo(FieldDecision.Status.REJECTED);
        assertThat(engine.getFieldSchema("flag").getType()).isEqualTo(FieldType.INTEGER);
    }

    @Test
    void integerBatchIntoDecimalFieldIsAccepted() {
        engine.ingestBatch("b1", Rows.column("price", "1.5", "2.5"));
        IngestReport report = engine.ingestBatch("b2", Rows.column("price", "3", "4"));
        assertThat(report.getDecision("price").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(engine.getFieldSchema("price").getType()).isEqualTo(FieldType.DECIMAL);
        assertThat(engine.getEvolutionHistory()).isEmpty();
    }

    @Test
    void stringFieldAcceptsAnyValues() {
        engine.ingestBatch("b1", Rows.column("note", "a", "b"));
        IngestReport report = engine.ingestBatch("b2", Rows.column("note", "1", "true", "2026-01-01"));
        assertThat(report.getDecision("note").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(engine.getFieldSchema("note").getType()).isEqualTo(FieldType.STRING);
    }

    @Test
    void rejectedBatchLeavesHistoryAndOtherFieldsIntact() {
        engine.ingestBatch("b1", List.of(
                Rows.row("age", "1", "name", "alice"),
                Rows.row("age", "2", "name", "bob")));
        IngestReport report = engine.ingestBatch("b2", List.of(
                Rows.row("age", "x", "name", "carol"),
                Rows.row("age", "y", "name", "dave")));
        assertThat(report.isAccepted()).isFalse();
        assertThat(report.getDecision("age").status()).isEqualTo(FieldDecision.Status.REJECTED);
        assertThat(report.getDecision("name").status()).isEqualTo(FieldDecision.Status.ACCEPTED);
        assertThat(engine.getConflictedFields()).contains("age");
    }
}
