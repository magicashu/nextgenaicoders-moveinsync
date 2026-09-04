package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyFinding;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyThresholds;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single-metric materiality rule kept for the workflow seam. The rule is the profiled default:
 * at least 300 trips, +3 percentage points and +25% relative against the prior four complete weeks.
 */
@Service
public final class AnomalyService {

    private final AnomalyThresholds thresholds;

    public AnomalyService() {
        this(AnomalyThresholds.DEFAULT);
    }

    public AnomalyService(AnomalyThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public AnomalyFinding assess(MetricResult metric) {
        if (metric.status() != MetricStatus.SUPPORTED || metric.delta() == null || metric.baselineValue() == null) {
            return new AnomalyFinding(false, "UNSUPPORTED", "Metric is unsupported or has no baseline; no anomaly claim is made.", thresholds.ruleVersion());
        }
        boolean enoughVolume = metric.supportingCount() >= thresholds.minimumVolume();
        BigDecimal relative = relativeChange(metric.value(), metric.baselineValue());
        boolean absolute = metric.delta().compareTo(thresholds.absolutePointGap()) >= 0;
        boolean relativeRise = relative != null && relative.compareTo(thresholds.relativeRise()) >= 0;
        boolean material = enoughVolume && absolute && relativeRise;
        String severity;
        if (!enoughVolume) {
            severity = "LOW_VOLUME";
        } else if (material && relative.compareTo(thresholds.highSeverityRelativeRise()) >= 0) {
            severity = "HIGH";
        } else if (material) {
            severity = "MEDIUM";
        } else {
            severity = "HEALTHY";
        }
        String summary = material
                ? "%s rose materially against the prior four complete weeks (+%s points, +%s%% relative, %d in population)."
                        .formatted(metric.metricName(), metric.delta().setScale(2, RoundingMode.HALF_UP),
                                relative.movePointRight(2).setScale(0, RoundingMode.HALF_UP), metric.supportingCount())
                : !enoughVolume
                        ? "%s has %d in population, below the %d minimum; no anomaly claim is made."
                                .formatted(metric.metricName(), metric.supportingCount(), thresholds.minimumVolume())
                        : "No material %s deterioration was detected against the prior four complete weeks.".formatted(metric.metricName().toLowerCase(java.util.Locale.ROOT));
        return new AnomalyFinding(material, severity, summary, thresholds.ruleVersion());
    }

    static BigDecimal relativeChange(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null || baseline.signum() == 0) {
            return null;
        }
        return current.subtract(baseline).divide(baseline, 4, RoundingMode.HALF_UP);
    }
}
