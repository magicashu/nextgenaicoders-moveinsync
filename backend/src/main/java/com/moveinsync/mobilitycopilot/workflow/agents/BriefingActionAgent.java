package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyFinding;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public final class BriefingActionAgent {

    public DecisionBrief draft(
            WorkflowState state,
            InvestigationAgent.InvestigationResult investigation,
            AnomalyFinding anomaly,
            EvidenceBundle evidence) {
        var metric = investigation.headlineMetric();
        String headline = anomaly.material()
                ? "%s: delayed-trip rate increased to %s%%".formatted(
                        state.tenant().businessUnit(), metric.valuePercent())
                : "%s: delayed-trip rate is within the materiality rule".formatted(
                        state.tenant().businessUnit());
        ActionProposal action = new ActionProposal(
                UUID.randomUUID(),
                "CREATE_WATCHLIST",
                "Create a site-shift watchlist",
                "Investigate the material M01 deterioration before assigning vendor blame.",
                "DRAFT_REQUIRES_APPROVAL");
        List<String> findings = List.of(
                anomaly.summary(),
                "Current: %s%%; prior four weeks: %s%%; change: %s percentage points."
                        .formatted(metric.valuePercent(), metric.baselinePercent(), metric.deltaPercentagePoints()),
                "The sample demonstrates the workflow contract; worker-specific attribution is the next slice.");
        return new DecisionBrief(
                state.runId(),
                state.tenant().businessUnit(),
                state.asOfDate(),
                headline,
                metric,
                findings,
                action,
                evidence,
                "AWAITING_APPROVAL");
    }
}
