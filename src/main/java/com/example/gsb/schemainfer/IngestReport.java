package com.example.gsb.schemainfer;

import java.util.Collections;
import java.util.List;

/** Result of ingesting one batch: per-field decisions plus the raw inference. */
public final class IngestReport {

    private final String batchId;
    private final List<FieldDecision> decisions;
    private final BatchInference inference;

    IngestReport(String batchId, List<FieldDecision> decisions, BatchInference inference) {
        this.batchId = batchId;
        this.decisions = Collections.unmodifiableList(decisions);
        this.inference = inference;
    }

    public String getBatchId() {
        return batchId;
    }

    public List<FieldDecision> getDecisions() {
        return decisions;
    }

    public FieldDecision getDecision(String fieldName) {
        return decisions.stream().filter(d -> d.fieldName().equals(fieldName)).findFirst().orElse(null);
    }

    /** Batch is accepted only when no field was rejected. */
    public boolean isAccepted() {
        return decisions.stream().noneMatch(FieldDecision::isRejected);
    }

    public List<FieldDecision> getAddedFields() {
        return decisions.stream().filter(d -> d.status() == FieldDecision.Status.ADDED).toList();
    }

    public List<FieldDecision> getEvolvedFields() {
        return decisions.stream().filter(d -> d.status() == FieldDecision.Status.EVOLVED).toList();
    }

    public List<FieldDecision> getRejectedFields() {
        return decisions.stream().filter(FieldDecision::isRejected).toList();
    }

    public BatchInference getInference() {
        return inference;
    }
}
