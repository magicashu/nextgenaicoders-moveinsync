package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.anomaly.application.AnomalyService;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorAgent;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowEngine;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public final class DeterministicWorkflowEngine implements WorkflowEngine {

    private final SupervisorAgent supervisor;
    private final InvestigationAgent investigator;
    private final EvidenceCriticAgent critic;
    private final BriefingActionAgent briefingAgent;
    private final AnomalyService anomalyService;

    public DeterministicWorkflowEngine(
            SupervisorAgent supervisor,
            InvestigationAgent investigator,
            EvidenceCriticAgent critic,
            BriefingActionAgent briefingAgent,
            AnomalyService anomalyService) {
        this.supervisor = supervisor;
        this.investigator = investigator;
        this.critic = critic;
        this.briefingAgent = briefingAgent;
        this.anomalyService = anomalyService;
    }

    @Override
    public WorkflowOutcome run(WorkflowState initialState) {
        var tasks = supervisor.plan(initialState);
        var investigation = investigator.investigate(initialState, tasks);
        var anomaly = anomalyService.assess(investigation.headlineMetric());
        var evidence = critic.verify(initialState.tenant(), investigation);
        var brief = briefingAgent.draft(initialState, investigation, anomaly, evidence);
        var auditEvents = List.of(
                event(initialState, "WORKFLOW_STARTED"),
                event(initialState, "EVIDENCE_VERIFIED"),
                event(initialState, "BRIEF_CREATED"),
                event(initialState, "ACTION_AWAITING_APPROVAL"));
        return new WorkflowOutcome(brief, WorkflowStep.AWAITING_APPROVAL, auditEvents);
    }

    private AuditEvent event(WorkflowState state, String eventType) {
        return new AuditEvent(
                UUID.randomUUID(), state.runId(), state.tenant().businessUnit(), eventType, Instant.now());
    }
}
