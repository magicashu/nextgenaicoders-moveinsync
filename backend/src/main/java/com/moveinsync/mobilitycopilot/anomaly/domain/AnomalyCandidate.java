package com.moveinsync.mobilitycopilot.anomaly.domain;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** One deterministic anomaly candidate with its benchmark, impact, confidence and ranking score. */
public record AnomalyCandidate(
        String anomalyId,
        String businessUnit,
        MetricId metricId,
        MetricResult metric,
        AnomalyClassification classification,
        String severity,
        BigDecimal deltaPoints,
        BigDecimal relativeChange,
        BigDecimal configuredTarget,
        Boolean meetsConfiguredTarget,
        ImpactEstimate impact,
        ConfidenceComponents confidence,
        BigDecimal priorityScore,
        String ruleVersion,
        List<String> reasons) {

    public AnomalyCandidate {
        Objects.requireNonNull(anomalyId);
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(metricId);
        Objects.requireNonNull(metric);
        Objects.requireNonNull(classification);
        Objects.requireNonNull(ruleVersion);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean material() {
        return classification == AnomalyClassification.OPERATIONAL_ANOMALY;
    }
}
