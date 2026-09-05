package com.moveinsync.mobilitycopilot.anomaly.domain;

/** Deterministic impact derived from governed counts, never from model text. */
public record ImpactEstimate(
        long currentNumerator,
        long currentDenominator,
        long excessEvents,
        long affectedRiderLegs,
        long excessRiderLegs,
        String method) {
}
