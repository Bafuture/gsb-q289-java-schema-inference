package com.example.gsb.schemainfer;

/**
 * Outcome of reconciling one field of an ingested batch against the
 * established schema.
 */
public record FieldDecision(String fieldName, Status status, String message) {

    public enum Status {
        /** Field was not known before and has been registered in the schema. */
        ADDED,
        /** Batch is consistent with the established schema; nothing changed. */
        ACCEPTED,
        /** Schema was explicitly extended (e.g. INTEGER -> DECIMAL). Recorded in history. */
        EVOLVED,
        /** Batch is incompatible with the established schema; schema left unchanged. */
        REJECTED
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }
}
