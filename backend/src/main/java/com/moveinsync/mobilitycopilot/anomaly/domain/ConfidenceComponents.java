package com.moveinsync.mobilitycopilot.anomaly.domain;

import java.math.BigDecimal;

/** Confidence is computed from data coverage, volume, magnitude and freshness, not the model's self-rating. */
public record ConfidenceComponents(
        BigDecimal volume,
        BigDecimal magnitude,
        BigDecimal coverage,
        BigDecimal freshness,
        BigDecimal overall) {
}
