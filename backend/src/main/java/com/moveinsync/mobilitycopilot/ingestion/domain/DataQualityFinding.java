package com.moveinsync.mobilitycopilot.ingestion.domain;

import java.util.Objects;

/**
 * One deterministic data-quality observation (Q1-Q14 in the dataset profile).
 * Findings are descriptive; the handling rule is applied inside the normalised SQL.
 */
public record DataQualityFinding(
        String code,
        String description,
        long affectedRows,
        String handling,
        Severity severity) {

    public enum Severity { INFO, WARNING, BLOCKING }

    public DataQualityFinding {
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(handling, "handling is required");
        Objects.requireNonNull(severity, "severity is required");
        if (affectedRows < 0) {
            throw new IllegalArgumentException("affectedRows must be non-negative");
        }
    }
}
