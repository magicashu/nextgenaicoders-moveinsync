package com.moveinsync.mobilitycopilot.workflow.application.ports;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The only door from the workflow into governed analytics. Every method is tenant-scoped, read-only
 * and returns typed evidence with provenance. The workflow never sees SQL, rows or a connection.
 *
 * <p>Shapes mirror the governed-analytics workstream one to one
 * ({@code AnomalyDetectionResult}, {@code WorkerContributionTools.run}, {@code CapabilityMatrix},
 * {@code ContributionService.crossTenantDelayedTripRate}) so the composition root adapts by mapping.
 */
public interface AnalyticsGateway {

    /** Deterministic sensing: metric snapshot, anomaly candidates and data-quality notes for one tenant and as-of date. */
    DetectionSnapshot detect(TenantContext tenant, LocalDate asOfDate);

    /** Runs one of the seven allowlisted worker tools. Unknown workers are rejected by the gateway. */
    WorkerEvidenceDto runWorker(String worker, TenantContext tenant, WindowDto current, WindowDto baseline, Map<String, String> filters);

    /** Governed single-metric query (compare_metric / get_metric). */
    MetricResult metric(MetricQuery query);

    /** Per-tenant capability statement for the loaded data version. */
    List<CapabilityGap> capabilities(TenantContext tenant);

    /** Cross-tenant peers for one metric window; the engine exposes this only to the facilities-head persona. */
    List<PeerValueDto> crossTenantPeers(MetricId metricId, WindowDto window);

    record WindowDto(LocalDate start, LocalDate end) {
        public static WindowDto trailingWeek(LocalDate asOf) {
            return new WindowDto(asOf.minusDays(7), asOf.minusDays(1));
        }

        public WindowDto priorFourWeeks() {
            LocalDate end = start.minusDays(1);
            return new WindowDto(end.minusDays(27), end);
        }
    }

    record CapabilityGap(String analysis, String support, String reason, List<MetricId> metrics) {
        public boolean unsupported() {
            return "UNSUPPORTED".equals(support);
        }

        public boolean derivable() {
            return "DERIVABLE".equals(support);
        }
    }

    record PeerValueDto(String businessUnit, long numerator, long denominator, java.math.BigDecimal value) {
    }
}
