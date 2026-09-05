package com.moveinsync.mobilitycopilot.evidence.domain;

import java.util.Objects;

/** Untrusted semantic observation returned by the optional language-model critic. */
public record CritiqueIssue(Type type, Severity severity, String explanation) {
    public CritiqueIssue {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(explanation, "explanation is required");
    }

    public enum Type {
        UNSUPPORTED_CLAIM, UNSUPPORTED_ATTRIBUTION, UNSUPPORTED_CAUSALITY,
        MISSING_COMPARISON, MISSING_CAVEAT, CONTRADICTORY_EVIDENCE,
        INSUFFICIENT_EVIDENCE, INVALID_REFERENCE, SCOPE_MISMATCH, DATA_VERSION_MISMATCH
    }

    public enum Severity { LOW, MEDIUM, HIGH }
}
