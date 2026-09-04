package com.moveinsync.mobilitycopilot.workflow.adapter.scaffold;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.application.AnomalyService;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyFinding;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gateway over the scaffold's single-metric seam (M01 in DuckDB plus the anomaly rule). It lets this
 * branch boot and demonstrate the healthy/anomaly/approval routes on the tiny fixture. The
 * composition root replaces it with the governed-analytics adapter
 * ({@code mobility.workflow.analytics-gateway=governed}).
 */
@Component
@ConditionalOnProperty(name = "mobility.workflow.analytics-gateway", havingValue = "scaffold", matchIfMissing = true)
public class ScaffoldAnalyticsGateway implements AnalyticsGateway {

    private final MetricService metrics;
    private final AnomalyService anomalies;

    public ScaffoldAnalyticsGateway(MetricService metrics, AnomalyService anomalies) {
        this.metrics = metrics;
        this.anomalies = anomalies;
    }

    @Override
    public DetectionSnapshot detect(TenantContext tenant, LocalDate asOfDate) {
        MetricResult m01 = metrics.delayedTripRate(tenant, asOfDate);
        AnomalyFinding finding = anomalies.assess(m01);
        BigDecimal relative = m01.baselineValue() == null || m01.baselineValue().signum() == 0 || m01.delta() == null ? null
                : m01.delta().divide(m01.baselineValue(), 4, java.math.RoundingMode.HALF_UP);
        long excess = m01.numerator() == null || m01.denominator() == null || m01.baselineValue() == null ? 0
                : Math.max(0, Math.round(m01.numerator().doubleValue() - m01.baselineValue().doubleValue() * m01.denominator().doubleValue() / 100));
        DetectionSnapshot.IssueCandidate candidate = new DetectionSnapshot.IssueCandidate(
                tenant.businessUnit() + ":m01_delayed_trip_rate:" + m01.periodEnd(), MetricId.M01_DELAYED_TRIP_RATE, m01,
                finding.material() ? "OPERATIONAL_ANOMALY" : "HEALTHY", finding.severity(), m01.delta(), relative, new BigDecimal("10.00"),
                m01.value() != null && m01.value().compareTo(new BigDecimal("10.00")) <= 0, excess, 0, 0,
                new BigDecimal("0.60"), finding.material() ? BigDecimal.TEN : BigDecimal.ZERO, List.of(finding.summary()));
        return new DetectionSnapshot(tenant.businessUnit(), asOfDate, m01.dataVersion(), finding.ruleVersion(), List.of(candidate), List.of());
    }

    @Override
    public WorkerEvidenceDto runWorker(String worker, TenantContext tenant, WindowDto current, WindowDto baseline, Map<String, String> filters) {
        return new WorkerEvidenceDto(worker, tenant.businessUnit(), List.of(), List.of(), List.of(), List.of(),
                List.of("Worker '" + worker + "' is not available on the scaffold analytics seam"), false);
    }

    @Override
    public MetricResult metric(MetricQuery query) {
        return metrics.query(query);
    }

    @Override
    public List<CapabilityGap> capabilities(TenantContext tenant) {
        return List.of(
                new CapabilityGap("delay", "SUPPORTED", "Ride files present", List.of(MetricId.M01_DELAYED_TRIP_RATE)),
                new CapabilityGap("vendor_peer", "UNSUPPORTED", "Scaffold seam exposes M01 only", List.of()),
                new CapabilityGap("site_shift_direction", "UNSUPPORTED", "Scaffold seam exposes M01 only", List.of()),
                new CapabilityGap("cost_per_trip", "UNSUPPORTED", "Scaffold seam exposes M01 only", List.of(MetricId.M09_MEDIAN_COST_PER_TRIP)),
                new CapabilityGap("feedback", "UNSUPPORTED", "Scaffold seam exposes M01 only", List.of(MetricId.M11_LOW_DRIVER_RATING_RATE)),
                new CapabilityGap("safety_alerts", "UNSUPPORTED", "Scaffold seam exposes M01 only", List.of(MetricId.M13_ALERT_RATE)),
                new CapabilityGap("no_show_roster", "UNSUPPORTED", "Scaffold seam exposes M01 only", List.of(MetricId.M06_NO_SHOW_RATE)));
    }

    @Override
    public List<PeerValueDto> crossTenantPeers(MetricId metricId, WindowDto window) {
        return List.of();
    }

    static String slug(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
