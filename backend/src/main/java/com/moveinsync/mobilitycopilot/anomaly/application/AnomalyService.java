package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyFinding;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public final class AnomalyService {

    private static final BigDecimal MATERIAL_DELTA_PP = new BigDecimal("5.00");

    public AnomalyFinding assess(MetricResult metric) {
        boolean material = metric.denominator() >= 5
                && metric.deltaPercentagePoints().compareTo(MATERIAL_DELTA_PP) >= 0;
        String severity = material && metric.deltaPercentagePoints().compareTo(new BigDecimal("10.00")) >= 0
                ? "HIGH"
                : material ? "MEDIUM" : "HEALTHY";
        String summary = material
                ? "Delayed-trip rate rose materially against the prior four complete weeks."
                : "No material delayed-trip-rate deterioration was detected.";
        return new AnomalyFinding(material, severity, summary, "M01-delta-v1");
    }
}
