package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyCandidate;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyClassification;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyDetectionResult;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyThresholds;
import com.moveinsync.mobilitycopilot.anomaly.domain.ConfidenceComponents;
import com.moveinsync.mobilitycopilot.anomaly.domain.ImpactEstimate;
import com.moveinsync.mobilitycopilot.anomaly.domain.RegimeChangeFinding;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.GovernedSqlTemplate;
import com.moveinsync.mobilitycopilot.metrics.application.CapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.application.ContributionService;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.ConfiguredTarget;
import com.moveinsync.mobilitycopilot.metrics.domain.ConfiguredTargets;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricDefinition;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRegistry;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DeterministicAnomalyDetectionService implements AnomalyDetectionService {

    /** Rate metrics scanned on every run, in safety-first order. */
    static final List<MetricId> SENSED_METRICS = List.of(
            MetricId.M14_SEVERE_ALERT_RATE,
            MetricId.M01_DELAYED_TRIP_RATE,
            MetricId.M04_ON_TIME_PICKUP_RATE,
            MetricId.M05_ON_TIME_DROP_RATE,
            MetricId.M06_NO_SHOW_RATE,
            MetricId.M11_LOW_DRIVER_RATING_RATE,
            MetricId.M16_TRACKING_GAP_RATE,
            MetricId.M13_ALERT_RATE);

    private final MetricService metrics;
    private final ContributionService contributions;
    private final CapabilityMatrixService capabilities;
    private final AnalyticsStore store;
    private final AnomalyThresholds thresholds;
    private final RegimeChangeDetector regimeChangeDetector = new RegimeChangeDetector();

    @org.springframework.beans.factory.annotation.Autowired
    public DeterministicAnomalyDetectionService(MetricService metrics, ContributionService contributions,
                                                CapabilityMatrixService capabilities, AnalyticsStore store) {
        this(metrics, contributions, capabilities, store, AnomalyThresholds.DEFAULT);
    }

    public DeterministicAnomalyDetectionService(MetricService metrics, ContributionService contributions,
                                                CapabilityMatrixService capabilities, AnalyticsStore store,
                                                AnomalyThresholds thresholds) {
        this.metrics = metrics;
        this.contributions = contributions;
        this.capabilities = capabilities;
        this.store = store;
        this.thresholds = thresholds;
    }

    @Override
    public AnomalyDetectionResult detect(TenantContext tenant, LocalDate asOfDate) {
        MetricWindow current = MetricWindow.trailingWeek(asOfDate);
        MetricWindow baseline = MetricWindow.priorFourWeeks(current);
        var matrix = capabilities.matrix(tenant);
        List<AnomalyCandidate> candidates = new ArrayList<>();
        for (MetricId metricId : SENSED_METRICS) {
            if (matrix.isUnsupported(metricId)) {
                continue;
            }
            MetricResult result = metrics.query(new MetricQuery(tenant, metricId, current.start(), current.end(),
                    baseline.start(), baseline.end(), Map.of()));
            if (result.status() != MetricStatus.SUPPORTED) {
                continue;
            }
            candidates.add(classify(tenant, result, current, matrix.forMetric(metricId)));
        }
        candidates.sort(Comparator.comparing(AnomalyCandidate::priorityScore, Comparator.nullsLast(Comparator.reverseOrder())));
        List<RegimeChangeFinding> notes = dataRegimeChanges(tenant);
        return new AnomalyDetectionResult(tenant.businessUnit(), asOfDate, store.catalog().dataVersion(),
                thresholds.ruleVersion(), candidates, notes);
    }

    @Override
    public List<RegimeChangeFinding> dataRegimeChanges(TenantContext tenant) {
        if (!store.catalog().isPresent(com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile.ALERTS)) {
            return List.of();
        }
        var rendered = GovernedSqlTemplate.render(SqlResources.read("sql/contributions/weekly_alert_types.sql"), tenant.businessUnit(),
                new MetricWindow(LocalDate.MIN.plusDays(1), LocalDate.MAX.minusDays(1)), Map.of(), Set.of(), Set.of(), Optional.empty());
        List<RegimeChangeDetector.WeeklyCount> weekly = new ArrayList<>();
        try (Connection connection = store.borrow(); PreparedStatement statement = connection.prepareStatement(rendered.sql())) {
            for (int i = 0; i < rendered.parameters().size(); i++) {
                statement.setObject(i + 1, rendered.parameters().get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    weekly.add(new RegimeChangeDetector.WeeklyCount(rs.getString("event_type"),
                            rs.getObject("week_start", LocalDate.class), rs.getLong("alerts")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read weekly alert snapshot", e);
        }
        return regimeChangeDetector.detect(tenant.businessUnit(), weekly);
    }

    AnomalyCandidate classify(TenantContext tenant, MetricResult result, MetricWindow current, Optional<CapabilityStatus> capability) {
        MetricDefinition definition = MetricRegistry.definition(result.metricId());
        List<String> reasons = new ArrayList<>();
        BigDecimal delta = result.delta();
        BigDecimal adverseDelta = definition.lowerIsBetter() ? delta : (delta == null ? null : delta.negate());
        BigDecimal relative = relativeChange(result.value(), result.baselineValue(), definition.lowerIsBetter());
        boolean enoughVolume = result.supportingCount() >= thresholds.minimumVolume();
        AnomalyClassification classification;
        String severity;
        if (!enoughVolume) {
            classification = AnomalyClassification.LOW_VOLUME;
            severity = "LOW_VOLUME";
            reasons.add("Population " + result.supportingCount() + " below minimum volume " + thresholds.minimumVolume());
        } else if (adverseDelta == null || relative == null) {
            classification = AnomalyClassification.HEALTHY;
            severity = "HEALTHY";
            reasons.add("No baseline available for comparison");
        } else if (isMaterial(result.metricId(), adverseDelta, relative, result)) {
            classification = AnomalyClassification.OPERATIONAL_ANOMALY;
            severity = result.metricId() == MetricId.M14_SEVERE_ALERT_RATE || relative.compareTo(thresholds.highSeverityRelativeRise()) >= 0 ? "HIGH" : "MEDIUM";
            reasons.add("Adverse change of %s points (%s%% relative) exceeds %s points and %s%%".formatted(
                    adverseDelta.setScale(2, RoundingMode.HALF_UP), pct(relative), thresholds.absolutePointGap(), pct(thresholds.relativeRise())));
        } else {
            classification = AnomalyClassification.HEALTHY;
            severity = "HEALTHY";
            reasons.add("Change of %s points (%s%% relative) is within the materiality rule".formatted(
                    adverseDelta.setScale(2, RoundingMode.HALF_UP), pct(relative)));
        }
        Optional<ConfiguredTarget> target = ConfiguredTargets.defaultFor(result.metricId());
        Boolean meetsTarget = target.map(t -> t.isMet(result.value())).orElse(null);
        target.ifPresent(t -> reasons.add((Boolean.TRUE.equals(meetsTarget) ? "Meets" : "Misses") + " " + t.label().toLowerCase(Locale.ROOT) + " of " + t.value()));
        ImpactEstimate impact = impact(tenant, result, current);
        ConfidenceComponents confidence = confidence(result, relative, capability);
        BigDecimal score = classification == AnomalyClassification.OPERATIONAL_ANOMALY ? score(result, relative, impact, confidence) : BigDecimal.ZERO;
        String anomalyId = "%s:%s:%s".formatted(tenant.businessUnit(), result.metricId().name().toLowerCase(Locale.ROOT), current.end());
        return new AnomalyCandidate(anomalyId, tenant.businessUnit(), result.metricId(), result, classification, severity,
                adverseDelta, relative, target.map(ConfiguredTarget::value).orElse(null), meetsTarget, impact, confidence, score,
                thresholds.ruleVersion(), reasons);
    }

    private boolean isMaterial(MetricId metricId, BigDecimal adverseDelta, BigDecimal relative, MetricResult result) {
        if (metricId == MetricId.M14_SEVERE_ALERT_RATE) {
            // any doubling of the Sev-1/2 rate is material when the baseline had events
            return result.baselineValue() != null && result.baselineValue().signum() > 0
                    && result.value().compareTo(result.baselineValue().multiply(thresholds.severeAlertMultiplier())) >= 0;
        }
        if (metricId == MetricId.M16_TRACKING_GAP_RATE || metricId == MetricId.M13_ALERT_RATE) {
            // per-1,000 metrics: doubling rule, no percentage-point gap
            return result.baselineValue() != null && result.baselineValue().signum() > 0
                    && result.value().compareTo(result.baselineValue().multiply(thresholds.severeAlertMultiplier())) >= 0;
        }
        return adverseDelta.compareTo(thresholds.absolutePointGap()) >= 0 && relative.compareTo(thresholds.relativeRise()) >= 0;
    }

    private ImpactEstimate impact(TenantContext tenant, MetricResult result, MetricWindow current) {
        long numerator = result.numerator() == null ? 0 : result.numerator().longValue();
        long denominator = result.denominator() == null ? 0 : result.denominator().longValue();
        long excess = 0;
        if (result.baselineValue() != null && result.unit() == com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.PERCENT && denominator > 0) {
            BigDecimal expected = result.baselineValue().multiply(BigDecimal.valueOf(denominator)).movePointLeft(2);
            excess = Math.max(0, Math.round(numerator - expected.doubleValue()));
        }
        if (result.metricId() == MetricId.M01_DELAYED_TRIP_RATE) {
            ContributionService.ImpactCounts counts = contributions.delayedRiderLegs(tenant, current, Map.of());
            long excessLegs = counts.delayedTrips() == 0 ? 0 : Math.round(excess * (counts.delayedRiderLegs() / (double) counts.delayedTrips()));
            return new ImpactEstimate(numerator, denominator, excess, counts.delayedRiderLegs(), excessLegs,
                    "excess delayed trips = current delayed - baseline rate x current trips; rider legs = actualemployee_cnt on delayed trips");
        }
        return new ImpactEstimate(numerator, denominator, excess, 0, 0, "excess events = current numerator - baseline rate x current denominator");
    }

    private ConfidenceComponents confidence(MetricResult result, BigDecimal relative, Optional<CapabilityStatus> capability) {
        BigDecimal volume = clamp(BigDecimal.valueOf(result.supportingCount()).divide(BigDecimal.valueOf(thresholds.minimumVolume() * 4L), 4, RoundingMode.HALF_UP));
        BigDecimal magnitude = relative == null ? BigDecimal.ZERO : clamp(relative.abs().divide(thresholds.relativeRise().multiply(BigDecimal.valueOf(4)), 4, RoundingMode.HALF_UP));
        BigDecimal coverage = capability.map(c -> switch (c.support()) {
            case SUPPORTED -> BigDecimal.ONE;
            case DERIVABLE -> new BigDecimal("0.5");
            case UNSUPPORTED -> BigDecimal.ZERO;
        }).orElse(BigDecimal.ONE);
        BigDecimal freshness = BigDecimal.ONE;
        BigDecimal overall = volume.multiply(new BigDecimal("0.35")).add(magnitude.multiply(new BigDecimal("0.25")))
                .add(coverage.multiply(new BigDecimal("0.30"))).add(freshness.multiply(new BigDecimal("0.10"))).setScale(2, RoundingMode.HALF_UP);
        return new ConfidenceComponents(volume, magnitude, coverage, freshness, overall);
    }

    private static BigDecimal score(MetricResult result, BigDecimal relative, ImpactEstimate impact, ConfidenceComponents confidence) {
        BigDecimal safety = result.metricId() == MetricId.M14_SEVERE_ALERT_RATE ? new BigDecimal("1000") : BigDecimal.ZERO;
        BigDecimal population = BigDecimal.valueOf(Math.log10(1 + impact.excessEvents() + impact.excessRiderLegs()));
        return safety.add(population.multiply(BigDecimal.TEN)).add(relative == null ? BigDecimal.ZERO : relative.abs())
                .multiply(confidence.overall()).setScale(3, RoundingMode.HALF_UP);
    }

    static BigDecimal relativeChange(BigDecimal current, BigDecimal baseline, boolean lowerIsBetter) {
        if (current == null || baseline == null || baseline.signum() == 0) {
            return null;
        }
        BigDecimal change = current.subtract(baseline).divide(baseline, 4, RoundingMode.HALF_UP);
        return lowerIsBetter ? change : change.negate();
    }

    private static BigDecimal clamp(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private static String pct(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
