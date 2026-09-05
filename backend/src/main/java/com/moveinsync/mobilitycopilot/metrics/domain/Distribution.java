package com.moveinsync.mobilitycopilot.metrics.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Distribution(
        String evidenceId,
        String businessUnit,
        MetricId metricId,
        String category,
        MetricWindow currentWindow,
        MetricWindow baselineWindow,
        Map<String, String> filters,
        long currentTotal,
        long baselineTotal,
        List<DistributionRow> rows,
        String contractVersion,
        String dataVersion,
        String source,
        List<String> caveats) {

    public Distribution {
        Objects.requireNonNull(evidenceId);
        Objects.requireNonNull(metricId);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        rows = rows == null ? List.of() : List.copyOf(rows);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }
}
