package com.moveinsync.mobilitycopilot.metrics.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ranked contribution of one dimension to one metric, current window versus baseline. */
public record ContributionRanking(
        String evidenceId,
        String businessUnit,
        MetricId metricId,
        Dimension dimension,
        MetricWindow currentWindow,
        MetricWindow baselineWindow,
        Map<String, String> filters,
        int minimumVolume,
        List<ContributionRow> rows,
        String contractVersion,
        String dataVersion,
        String source,
        List<String> caveats) {

    public ContributionRanking {
        Objects.requireNonNull(evidenceId);
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(metricId);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(currentWindow);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        rows = rows == null ? List.of() : List.copyOf(rows);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }

    public List<ContributionRow> qualifiedRows() {
        return rows.stream().filter(ContributionRow::qualified).toList();
    }

    /** True when every qualified member moved in the adverse direction (for "every vendor rose"). */
    public boolean allQualifiedIncreased() {
        List<ContributionRow> qualified = qualifiedRows();
        return !qualified.isEmpty() && qualified.stream().allMatch(r -> r.delta() != null && r.delta().signum() > 0);
    }
}
