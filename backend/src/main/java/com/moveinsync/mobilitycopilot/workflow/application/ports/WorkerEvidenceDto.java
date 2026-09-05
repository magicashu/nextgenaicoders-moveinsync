package com.moveinsync.mobilitycopilot.workflow.application.ports;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Mirror of the analytics {@code WorkerEvidence}: typed worker output with provenance. */
public record WorkerEvidenceDto(
        String worker,
        String businessUnit,
        List<MetricResult> metrics,
        List<Ranking> rankings,
        List<Distribution> distributions,
        List<String> directFindings,
        List<String> caveats,
        boolean supported) {

    public WorkerEvidenceDto {
        Objects.requireNonNull(worker);
        Objects.requireNonNull(businessUnit);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        rankings = rankings == null ? List.of() : List.copyOf(rankings);
        distributions = distributions == null ? List.of() : List.copyOf(distributions);
        directFindings = directFindings == null ? List.of() : List.copyOf(directFindings);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }

    public record Ranking(
            String evidenceId,
            MetricId metricId,
            String dimension,
            int minimumVolume,
            List<Row> rows,
            boolean allQualifiedIncreased,
            String source,
            String contractVersion,
            String dataVersion,
            List<String> caveats) {

        public Ranking {
            rows = rows == null ? List.of() : List.copyOf(rows);
            caveats = caveats == null ? List.of() : List.copyOf(caveats);
        }

        public List<Row> qualifiedRows() {
            return rows.stream().filter(Row::qualified).toList();
        }

        public record Row(String member, BigDecimal currentValue, BigDecimal baselineValue, BigDecimal delta,
                          long currentNumerator, long currentDenominator, BigDecimal shareOfCurrentNumerator, boolean qualified) {
        }
    }

    public record Distribution(
            String evidenceId,
            MetricId metricId,
            String category,
            long currentTotal,
            long baselineTotal,
            List<Row> rows,
            String source,
            String contractVersion,
            String dataVersion) {

        public Distribution {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public record Row(String category, long count, BigDecimal share, long baselineCount, BigDecimal baselineShare) {
        }
    }
}
