package com.moveinsync.mobilitycopilot.anomaly.domain;

public record AnomalyFinding(
        boolean material,
        String severity,
        String summary,
        String ruleVersion) {
}
