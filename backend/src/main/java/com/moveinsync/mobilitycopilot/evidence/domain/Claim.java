package com.moveinsync.mobilitycopilot.evidence.domain;

import java.util.List;
import java.util.Objects;

/** One sentence the product may display. Every numeric token must resolve to a cited evidence item. */
public record Claim(
        String claimId,
        String text,
        Kind kind,
        List<String> evidenceIds,
        String worker) {

    public enum Kind { DIRECT, INFERRED, CAVEAT, RECOMMENDATION }

    public Claim {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(text);
        Objects.requireNonNull(kind);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        worker = worker == null ? "system" : worker;
    }
}
