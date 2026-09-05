package com.moveinsync.mobilitycopilot.workflow.application.ports;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Mirror of the analytics {@code AnomalyDetectionResult}: the typed sensing output. */
public record DetectionSnapshot(
        String businessUnit,
        LocalDate asOfDate,
        String dataVersion,
        String ruleVersion,
        List<IssueCandidate> candidates,
        List<DataQualityNote> dataQualityNotes) {

    public DetectionSnapshot {
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(asOfDate);
        Objects.requireNonNull(dataVersion);
        Objects.requireNonNull(ruleVersion);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        dataQualityNotes = dataQualityNotes == null ? List.of() : List.copyOf(dataQualityNotes);
    }

    public List<IssueCandidate> material() {
        return candidates.stream().filter(IssueCandidate::material).toList();
    }

    public Optional<IssueCandidate> selected() {
        return material().stream().max(java.util.Comparator.comparing(IssueCandidate::priorityScore));
    }

    public boolean healthy() {
        return material().isEmpty();
    }

    public record IssueCandidate(
            String anomalyId,
            MetricId metricId,
            MetricResult metric,
            String classification,
            String severity,
            BigDecimal deltaPoints,
            BigDecimal relativeChange,
            BigDecimal configuredTarget,
            Boolean meetsConfiguredTarget,
            long excessEvents,
            long affectedRiderLegs,
            long excessRiderLegs,
            BigDecimal confidence,
            BigDecimal priorityScore,
            List<String> reasons) {

        public IssueCandidate {
            Objects.requireNonNull(anomalyId);
            Objects.requireNonNull(metricId);
            Objects.requireNonNull(metric);
            Objects.requireNonNull(classification);
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            priorityScore = priorityScore == null ? BigDecimal.ZERO : priorityScore;
            confidence = confidence == null ? BigDecimal.ZERO : confidence;
        }

        public boolean material() {
            return "OPERATIONAL_ANOMALY".equals(classification);
        }
    }

    /** A data-regime change or other quality note. Never an issue, never an action. */
    public record DataQualityNote(String findingId, String eventType, String note) {
    }
}
