package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyFinding;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Component
public final class BriefingActionAgent {

    private final WorkflowProperties properties;

    public BriefingActionAgent(WorkflowProperties properties) {
        this.properties = properties;
    }

    public DecisionBrief draft(
            WorkflowState state,
            InvestigationAgent.InvestigationResult investigation,
            AnomalyFinding anomaly,
            EvidenceBundle evidence) {
        var metric = investigation.headlineMetric();
        String headline = anomaly.material()
                ? "%s: delayed-trip rate increased to %s%%".formatted(
                        state.tenant().businessUnit(), metric.value())
                : "%s: delayed-trip rate is within the materiality rule".formatted(
                        state.tenant().businessUnit());
        Instant createdAt = Instant.now();
        ActionProposal action = new ActionProposal(
                UUID.randomUUID(),
                state.runId(),
                ActionType.CREATE_SITE_SHIFT_WATCHLIST,
                "Create a site-shift watchlist",
                "Investigate the material M01 deterioration before assigning vendor blame.",
                Map.of("businessUnit", state.tenant().businessUnit()),
                evidence.items().getFirst().dataVersion(),
                createdAt,
                createdAt.plus(properties.approvalTtl()),
                ActionStatus.DRAFT_REQUIRES_APPROVAL);
        List<String> findings = List.of(
                anomaly.summary(),
                "Current: %s%%; prior four weeks: %s%%; change: %s percentage points."
                        .formatted(metric.value(), metric.baselineValue(), metric.delta()),
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
