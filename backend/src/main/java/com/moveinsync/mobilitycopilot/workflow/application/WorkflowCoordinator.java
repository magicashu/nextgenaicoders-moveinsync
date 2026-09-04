package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Application entry point for scheduled runs, on-demand runs, contextual questions and approval resume. */
@Service
public final class WorkflowCoordinator {

    private final ResumableWorkflowEngine workflowEngine;
    private final WorkflowProperties properties;

    public WorkflowCoordinator(ResumableWorkflowEngine workflowEngine, WorkflowProperties properties) {
        this.workflowEngine = workflowEngine;
        this.properties = properties;
    }

    /** Scaffold-compatible demo entry: a scheduled transport-manager run for the tenant. */
    public DecisionBrief createDemoBrief(String businessUnit, LocalDate asOfDate) {
        ActorContext actor = new ActorContext("scheduler", businessUnit, Set.of("TRANSPORT_MANAGER"));
        return run(actor, new TenantContext(businessUnit), asOfDate, RunContext.Persona.TRANSPORT_MANAGER, RunContext.RequestMode.SCHEDULED, null).brief();
    }

    public WorkflowOutcome run(ActorContext actor, TenantContext tenant, LocalDate asOfDate, RunContext.Persona persona,
                               RunContext.RequestMode mode, String question) {
        WorkflowState state = WorkflowState.start(tenant, asOfDate, properties.maxInvestigationSteps(), properties.maxCorrectionCycles(), properties.maxToolCalls());
        RunContext context = new RunContext(actor, persona, mode, state.runId().toString(), RunContext.WORKFLOW_VERSION,
                RunContext.PROMPT_VERSION, "unset", "unknown", question);
        return workflowEngine.run(state, context);
    }

    public WorkflowOutcome resume(ActorContext actor, ApprovalDecision decision) {
        RunContext context = new RunContext(actor, RunContext.Persona.TRANSPORT_MANAGER, RunContext.RequestMode.RESUME,
                decision.runId().toString(), RunContext.WORKFLOW_VERSION, RunContext.PROMPT_VERSION, "unset", "unknown", null);
        return workflowEngine.resume(decision, context);
    }

    public Optional<WorkflowRun> find(UUID runId) {
        return workflowEngine.find(runId);
    }
}
