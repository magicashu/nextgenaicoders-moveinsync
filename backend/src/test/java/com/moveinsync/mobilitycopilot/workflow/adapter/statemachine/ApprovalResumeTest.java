package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import com.moveinsync.mobilitycopilot.workflow.support.EngineHarness;
import com.moveinsync.mobilitycopilot.workflow.support.G1Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalResumeTest {

    private final G1Fixtures analytics = new G1Fixtures();
    private final EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable());
    private final ActorContext approver = new ActorContext("manager-1", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER"));

    private ApprovalRequest pause() {
        WorkflowOutcome outcome = harness.run("pinnacle-Slc", G1Fixtures.AS_OF);
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.AWAITING_APPROVAL);
        return harness.engine.find(outcome.brief().runId()).orElseThrow().approvalRequest();
    }

    private ApprovalDecision decision(ApprovalRequest request, ApprovalDecisionType type, ActionProposal edited) {
        return new ApprovalDecision(request.approvalId(), request.proposal().actionId(), request.runId(), type, "manager-1", Instant.now(), "ok", edited);
    }

    private RunContext resumeContext(ActorContext actor, ApprovalRequest request) {
        return new RunContext(actor, RunContext.Persona.TRANSPORT_MANAGER, RunContext.RequestMode.RESUME, request.runId().toString(),
                RunContext.WORKFLOW_VERSION, RunContext.PROMPT_VERSION, "unset", "unknown", null);
    }

    @Test
    void approveRevalidatesAndExecutesExactlyOnceEvenWhenResumedTwice() {
        ApprovalRequest request = pause();
        ApprovalDecision approve = decision(request, ApprovalDecisionType.APPROVE, null);

        WorkflowOutcome first = harness.engine.resume(approve, resumeContext(approver, request));
        assertThat(first.finalStep()).isEqualTo(WorkflowStep.EXECUTED);
        assertThat(harness.actions.effectCount()).isEqualTo(1);
        WorkflowRun run = harness.engine.find(request.runId()).orElseThrow();
        assertThat(run.receipt().status()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(run.receipt().idempotencyKey()).isEqualTo(request.runId() + ":" + request.proposal().actionId());
        assertThat(first.auditEvents()).extracting("eventType").containsExactly("APPROVAL_APPROVE", "ACTION_REVALIDATED", "ACTION_EXECUTED", "RUN_SUMMARY");
        assertThat(harness.checkpoints.find(request.runId())).get().extracting(v -> v.state().step()).isEqualTo(WorkflowStep.EXECUTED);

        // duplicate resume: the checkpoint is no longer awaiting approval, so nothing executes again
        assertThatThrownBy(() -> harness.engine.resume(approve, resumeContext(approver, request))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not awaiting approval");
        assertThat(harness.actions.effectCount()).isEqualTo(1);
    }

    @Test
    void rejectNeverExecutes() {
        ApprovalRequest request = pause();
        WorkflowOutcome outcome = harness.engine.resume(decision(request, ApprovalDecisionType.REJECT, null), resumeContext(approver, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.REJECTED);
        assertThat(harness.actions.effectCount()).isZero();
        assertThat(outcome.auditEvents()).extracting("eventType").contains("APPROVAL_REJECT").doesNotContain("ACTION_EXECUTED");
    }

    @Test
    void staleEvidenceBlocksExecutionAfterApproval() {
        ApprovalRequest request = pause();
        analytics.headlineValue = new java.math.BigDecimal("18.40");   // data changed between pause and approval
        WorkflowOutcome outcome = harness.engine.resume(decision(request, ApprovalDecisionType.APPROVE, null), resumeContext(approver, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.APPROVED_NOT_EXECUTED);
        assertThat(harness.actions.effectCount()).isZero();
        assertThat(outcome.auditEvents()).extracting("eventType").contains("ACTION_NOT_EXECUTED");
        assertThat(outcome.auditEvents().stream().filter(e -> e.eventType().equals("ACTION_NOT_EXECUTED")).findFirst().orElseThrow().payload().get("reasons"))
                .contains("Evidence changed");
    }

    @Test
    void expiredApprovalCannotExecute() {
        ApprovalRequest request = pause();
        ApprovalRequest expired = new ApprovalRequest(request.approvalId(), request.runId(), request.businessUnit(), request.proposal(), request.evidenceVersion(),
                request.createdAt(), request.createdAt().plusMillis(1));
        harness.approvals.create(expired);
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        WorkflowOutcome outcome = harness.engine.resume(decision(request, ApprovalDecisionType.APPROVE, null), resumeContext(approver, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.EXPIRED);
        assertThat(harness.actions.effectCount()).isZero();
    }

    @Test
    void crossTenantApproverIsRefused() {
        ApprovalRequest request = pause();
        ActorContext stranger = new ActorContext("orbit-manager", "orbit-Slc", Set.of("TRANSPORT_MANAGER"));
        WorkflowOutcome outcome = harness.engine.resume(decision(request, ApprovalDecisionType.APPROVE, null), resumeContext(stranger, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.APPROVED_NOT_EXECUTED);
        assertThat(harness.actions.effectCount()).isZero();
    }

    @Test
    void editNarrowsScopeThenExecutesButCannotEscalate() {
        ApprovalRequest request = pause();
        ActionProposal original = request.proposal();
        Map<String, String> scope = new LinkedHashMap<>(original.scope());
        scope.put("watchDays", "3");
        ActionProposal narrowed = new ActionProposal(original.actionId(), original.runId(), original.type(), original.title(), original.rationale(), scope,
                original.evidenceVersion(), original.createdAt(), original.expiresAt(), original.status());
        WorkflowOutcome outcome = harness.engine.resume(decision(request, ApprovalDecisionType.EDIT, narrowed), resumeContext(approver, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.EXECUTED);
        assertThat(harness.actions.effectCount()).isEqualTo(1);

        ApprovalRequest second = pause();
        ActionProposal escalate = new ActionProposal(second.proposal().actionId(), second.proposal().runId(), ActionType.DRAFT_VENDOR_ESCALATION, "Escalate",
                "edited", second.proposal().scope(), second.proposal().evidenceVersion(), second.proposal().createdAt(), second.proposal().expiresAt(), second.proposal().status());
        WorkflowOutcome blocked = harness.engine.resume(decision(second, ApprovalDecisionType.EDIT, escalate), resumeContext(approver, second));
        assertThat(blocked.finalStep()).isEqualTo(WorkflowStep.APPROVED_NOT_EXECUTED);
        assertThat(harness.actions.effectCount()).isEqualTo(1);
    }

    @Test
    void adapterFailureKeepsApprovedNotExecutedWithAuditableError() {
        ApprovalRequest request = pause();
        harness.actions.failNextExecution();
        WorkflowOutcome outcome = harness.engine.resume(decision(request, ApprovalDecisionType.APPROVE, null), resumeContext(approver, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.APPROVED_NOT_EXECUTED);
        assertThat(harness.actions.effectCount()).isZero();
        assertThat(outcome.auditEvents().stream().filter(e -> e.eventType().equals("ACTION_NOT_EXECUTED")).findFirst().orElseThrow().payload().get("message"))
                .contains("Adapter failure");
    }

    @Test
    void resumeAfterRestartRebuildsEvidenceFromTheCheckpoint() {
        ApprovalRequest request = pause();
        // simulate a process restart: a new engine sharing only the durable ports
        WorkflowOutcome outcome = new DeterministicWorkflowEngineRestart(harness, analytics).resume(decision(request, ApprovalDecisionType.APPROVE, null), resumeContext(approver, request));
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.EXECUTED);
        assertThat(outcome.auditEvents()).extracting("eventType").contains("ACTION_REVALIDATED", "ACTION_EXECUTED");
    }

    @Test
    void unknownApprovalOrMismatchedActionIsRejected() {
        ApprovalRequest request = pause();
        ApprovalDecision wrongAction = new ApprovalDecision(request.approvalId(), UUID.randomUUID(), request.runId(), ApprovalDecisionType.APPROVE, "manager-1", Instant.now(), null, null);
        assertThatThrownBy(() -> harness.engine.resume(wrongAction, resumeContext(approver, request))).isInstanceOf(IllegalStateException.class);
        assertThat(harness.actions.effectCount()).isZero();
    }

    /** Builds a second engine over the ORIGINAL durable stores and the same analytics, with an empty in-process registry. */
    static final class DeterministicWorkflowEngineRestart {
        private final DeterministicWorkflowEngine engine;

        DeterministicWorkflowEngineRestart(EngineHarness durable, G1Fixtures analytics) {
            var verifier = new com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier();
            var model = new LanguageModelPort.Unavailable();
            var registry = new com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerToolRegistry(analytics);
            this.engine = new DeterministicWorkflowEngine(analytics, new com.moveinsync.mobilitycopilot.workflow.agents.SupervisorAgent(model, durable.properties),
                    new com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent(registry, model, durable.properties),
                    new com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgent(verifier, model, durable.properties),
                    new com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent(durable.properties, model), verifier, durable.authorizer, durable.checkpoints,
                    durable.approvals, durable.actions, durable.actions, durable.audit, durable.properties, model,
                    com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener.NONE);
        }

        WorkflowOutcome resume(ApprovalDecision decision, RunContext context) {
            return engine.resume(decision, context);
        }
    }
}
