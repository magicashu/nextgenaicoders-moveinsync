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
        if (metric.supportingCount() <= 0 || metric.value() == null || metric.baselineValue() == null) {
            throw new IllegalStateException("Evidence is incomplete; brief generation is blocked");
        }
        String evidenceId = "%s:%s:%s".formatted(
                tenant.businessUnit(),
                metric.metricId().name().toLowerCase(Locale.ROOT),
                metric.periodEnd());
        EvidenceItem item = new EvidenceItem(
                evidenceId,
                metric.metricId().name(),
                metric.value(),
                metric.unit().name(),
                metric.baselineValue(),
                metric.delta(),
                metric.numerator(),
                metric.denominator(),
                metric.supportingCount(),
                metric.periodStart(),
                metric.periodEnd(),
                metric.filters(),
                metric.source(),
                metric.contractVersion(),
                metric.dataVersion());
        return new EvidenceBundle(List.of(item), 1.0, metric.supportingCount(), metric.caveats());
    }
}
