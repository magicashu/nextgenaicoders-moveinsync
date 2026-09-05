package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public final class EvidenceCriticAgent {

    public EvidenceBundle verify(
            TenantContext tenant,
            InvestigationAgent.InvestigationResult investigation) {
        var metric = investigation.headlineMetric();
        if (metric.denominator() <= 0 || metric.valuePercent() == null || metric.baselinePercent() == null) {
            throw new IllegalStateException("Evidence is incomplete; brief generation is blocked");
        }
        String evidenceId = "%s:%s:%s".formatted(
                tenant.businessUnit(),
                metric.metricId().name().toLowerCase(Locale.ROOT),
                metric.periodEnd());
        EvidenceItem item = new EvidenceItem(
                evidenceId,
                metric.metricId().name(),
                metric.valuePercent(),
                metric.baselinePercent(),
                metric.numerator(),
                metric.denominator(),
                "sql/metrics/m01_delayed_trip_rate.sql",
                metric.contractVersion(),
                metric.dataVersion());
        return new EvidenceBundle(List.of(item), 1.0, metric.denominator(), List.of());
    }
}
