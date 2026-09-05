package com.moveinsync.mobilitycopilot.anomaly.domain;

/** Deterministic classification of a metric movement. */
public enum AnomalyClassification {
    OPERATIONAL_ANOMALY,
    DATA_REGIME_CHANGE,
    LOW_VOLUME,
    HEALTHY
}
