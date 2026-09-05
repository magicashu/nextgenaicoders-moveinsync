package com.moveinsync.mobilitycopilot.anomaly.tools;

import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.util.List;
import java.util.Objects;

/**
 * Typed output of one investigation worker tool: governed metrics, rankings and distributions with
 * provenance plus deterministic direct findings. Investigators consume this; they never see rows.
 */
public record WorkerEvidence(
        String worker,
        String businessUnit,
        List<MetricResult> metrics,
        List<ContributionRanking> rankings,
        List<Distribution> distributions,
        List<String> directFindings,
        List<String> caveats,
        boolean supported) {

    public WorkerEvidence {
        Objects.requireNonNull(worker);
        Objects.requireNonNull(businessUnit);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        rankings = rankings == null ? List.of() : List.copyOf(rankings);
        distributions = distributions == null ? List.of() : List.copyOf(distributions);
        directFindings = directFindings == null ? List.of() : List.copyOf(directFindings);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }

    public List<String> evidenceIds() {
        List<String> ids = new java.util.ArrayList<>();
        rankings.forEach(r -> ids.add(r.evidenceId()));
        distributions.forEach(d -> ids.add(d.evidenceId()));
        return ids;
    }
}
