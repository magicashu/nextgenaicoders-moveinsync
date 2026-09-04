package com.moveinsync.mobilitycopilot.workflow.domain;

import java.util.List;
import java.util.Objects;

/** Evidence Critic output: what is supported, what overclaims, what is missing. One cycle maximum. */
public record Critique(
        Verdict verdict,
        List<String> supportedClaimIds,
        List<String> overclaimClaimIds,
        List<String> missingCaveats,
        List<String> contradictions,
        List<String> notes,
        boolean modelAssisted) {

    public enum Verdict { PASS, REVISE, ABSTAIN }

    public Critique {
        Objects.requireNonNull(verdict);
        supportedClaimIds = supportedClaimIds == null ? List.of() : List.copyOf(supportedClaimIds);
        overclaimClaimIds = overclaimClaimIds == null ? List.of() : List.copyOf(overclaimClaimIds);
        missingCaveats = missingCaveats == null ? List.of() : List.copyOf(missingCaveats);
        contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
