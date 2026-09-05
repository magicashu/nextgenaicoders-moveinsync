package com.moveinsync.mobilitycopilot.workflow.support;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier;
import com.moveinsync.mobilitycopilot.workflow.adapter.inmemory.InMemoryControlPlane;
import com.moveinsync.mobilitycopilot.workflow.adapter.statemachine.DeterministicWorkflowEngine;
import com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorAgent;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerToolRegistry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Wires the engine with in-memory ports for tests. */
public final class EngineHarness {

    public final WorkflowProperties properties;
    public final InMemoryControlPlane.CheckpointStore checkpoints = new InMemoryControlPlane.CheckpointStore();
    public final InMemoryControlPlane.Approvals approvals = new InMemoryControlPlane.Approvals();
    public final InMemoryControlPlane.Audit audit = new InMemoryControlPlane.Audit();
    public final InMemoryControlPlane.Actions actions = new InMemoryControlPlane.Actions();
    public final InMemoryControlPlane.Authorizer authorizer = new InMemoryControlPlane.Authorizer();
    public final List<TransitionEvent> transitions = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final DeterministicWorkflowEngine engine;

    public EngineHarness(AnalyticsGateway analytics, LanguageModelPort model) {
        this(analytics, model, new WorkflowProperties(4, 1, 12, Duration.ofSeconds(10), Duration.ofMinutes(30)));
    }

    public EngineHarness(AnalyticsGateway analytics, LanguageModelPort model, WorkflowProperties properties) {
        this.properties = properties;
        WorkerToolRegistry registry = new WorkerToolRegistry(analytics);
        EvidenceVerifier verifier = new EvidenceVerifier();
        TransitionListener listener = transitions::add;
        this.engine = new DeterministicWorkflowEngine(analytics, new SupervisorAgent(model, properties), new InvestigationAgent(registry, model, properties),
                new EvidenceCriticAgent(verifier, model, properties), new BriefingActionAgent(properties, model), verifier, authorizer, checkpoints, approvals,
                actions, actions, audit, properties, model, listener);
    }

    public static ActorContext manager(String businessUnit) {
        return new ActorContext("manager-1", businessUnit, Set.of("TRANSPORT_MANAGER"));
    }

    public WorkflowOutcome run(String businessUnit, LocalDate asOf) {
        return run(manager(businessUnit), businessUnit, asOf, RunContext.Persona.TRANSPORT_MANAGER, null);
    }

    public WorkflowOutcome run(ActorContext actor, String businessUnit, LocalDate asOf, RunContext.Persona persona, String question) {
        WorkflowState state = WorkflowState.start(new TenantContext(businessUnit), asOf, properties.maxInvestigationSteps(), properties.maxCorrectionCycles(), properties.maxToolCalls());
        RunContext context = new RunContext(actor, persona, question == null ? RunContext.RequestMode.SCHEDULED : RunContext.RequestMode.QUESTION,
                state.runId().toString(), RunContext.WORKFLOW_VERSION, RunContext.PROMPT_VERSION, "unset", "unknown", question);
        return engine.run(state, context);
    }
}
