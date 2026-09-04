package com.moveinsync.mobilitycopilot.anomaly.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Output of one scheduled or requested detection run for one tenant. */
public record AnomalyDetectionResult(
        String businessUnit,
        LocalDate asOfDate,
        String dataVersion,
        String ruleVersion,
        List<AnomalyCandidate> candidates,
        List<RegimeChangeFinding> dataQualityNotes) {

    public AnomalyDetectionResult {
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(asOfDate);
        Objects.requireNonNull(dataVersion);
        Objects.requireNonNull(ruleVersion);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        dataQualityNotes = dataQualityNotes == null ? List.of() : List.copyOf(dataQualityNotes);
    }

    public List<AnomalyCandidate> materialCandidates() {
        return candidates.stream().filter(AnomalyCandidate::material).toList();
    }

    /** Highest-priority operational anomaly, if any. */
    public Optional<AnomalyCandidate> selectedIssue() {
        return materialCandidates().stream().findFirst();
    }

    public boolean healthy() {
        return materialCandidates().isEmpty();
    }
}
