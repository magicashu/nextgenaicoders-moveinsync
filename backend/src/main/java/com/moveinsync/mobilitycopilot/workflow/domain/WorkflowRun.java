package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Rich, in-process run state built around the frozen {@link WorkflowState}. Large data stays outside
 * (evidence ids and compact summaries only). The frozen state is what checkpoints persist; every
 * other slot is deterministically re-derivable from it.
 */
public final class WorkflowRun {

    private WorkflowState state;
    private RunContext context;
    private WorkflowNode currentNode = WorkflowNode.INITIALIZE_RUN;
    private final Instant startedAt;
    private DetectionSnapshot detection;
    private DetectionSnapshot.IssueCandidate selectedIssue;
    private List<AnalyticsGateway.CapabilityGap> capabilities = List.of();
    private InvestigationPlan plan;
    private Critique planFeedback;
    private final List<InvestigationResult> investigations = new ArrayList<>();
    private EvidencePackage evidence;
    private Critique critique;
    private VerificationResult verification;
    private BriefingOutput briefing;
    private ActionProposal action;
    private PolicyDecision policy;
    private ApprovalRequest approvalRequest;
    private ApprovalDecision approvalDecision;
    private ExecutionReceipt receipt;
    private final List<TransitionEvent> transitions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener transitionListener =
            com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener.NONE;
    private final List<WorkflowError> errors = new ArrayList<>();
    private final List<ModelUsage> modelUsage = new java.util.concurrent.CopyOnWriteArrayList<>();

    public WorkflowRun(WorkflowState state, RunContext context) {
        this.state = Objects.requireNonNull(state);
        this.context = Objects.requireNonNull(context);
        this.startedAt = Instant.now();
    }

    public WorkflowState state() { return state; }
    public RunContext context() { return context; }
    public WorkflowNode currentNode() { return currentNode; }
    public Instant startedAt() { return startedAt; }
    public DetectionSnapshot detection() { return detection; }
    public DetectionSnapshot.IssueCandidate selectedIssue() { return selectedIssue; }
    public List<AnalyticsGateway.CapabilityGap> capabilities() { return capabilities; }
    public InvestigationPlan plan() { return plan; }
    public Critique planFeedback() { return planFeedback; }
    public List<InvestigationResult> investigations() { return Collections.unmodifiableList(investigations); }
    public EvidencePackage evidence() { return evidence; }
    public Critique critique() { return critique; }
    public VerificationResult verification() { return verification; }
    public BriefingOutput briefing() { return briefing; }
    public ActionProposal action() { return action; }
    public PolicyDecision policy() { return policy; }
    public ApprovalRequest approvalRequest() { return approvalRequest; }
    public ApprovalDecision approvalDecision() { return approvalDecision; }
    public ExecutionReceipt receipt() { return receipt; }
    public List<TransitionEvent> transitions() { return Collections.unmodifiableList(transitions); }
    public List<WorkflowError> errors() { return Collections.unmodifiableList(errors); }
    public List<ModelUsage> modelUsage() { return Collections.unmodifiableList(modelUsage); }

    public void context(RunContext value) { this.context = value; }
    public void currentNode(WorkflowNode node) { this.currentNode = node; }
    public void detection(DetectionSnapshot value) { this.detection = value; }
    public void selectedIssue(DetectionSnapshot.IssueCandidate value) { this.selectedIssue = value; }
    public void capabilities(List<AnalyticsGateway.CapabilityGap> value) { this.capabilities = List.copyOf(value); }
    public void plan(InvestigationPlan value) { this.plan = value; }
    public void planFeedback(Critique value) { this.planFeedback = value; }
    public void addInvestigation(InvestigationResult value) { this.investigations.add(value); }
    public void clearInvestigations() { this.investigations.clear(); }
    public void evidence(EvidencePackage value) { this.evidence = value; }
    public void critique(Critique value) { this.critique = value; }
    public void verification(VerificationResult value) { this.verification = value; }
    public void briefing(BriefingOutput value) { this.briefing = value; }
    public void action(ActionProposal value) { this.action = value; }
    public void policy(PolicyDecision value) { this.policy = value; }
    public void approvalRequest(ApprovalRequest value) { this.approvalRequest = value; }
    public void approvalDecision(ApprovalDecision value) { this.approvalDecision = value; }
    public void receipt(ExecutionReceipt value) { this.receipt = value; }
    public void addTransition(TransitionEvent value) { this.transitions.add(value); }
    public void transitionListener(com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener value) { this.transitionListener = value; }
    public void emitTransition(TransitionEvent value) {
        addTransition(value);
        try { transitionListener.onTransition(value); }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(WorkflowRun.class).warn("Trace recording failed: run={} type={}", state.runId(), failure.getClass().getSimpleName());
        }
    }
    public void addError(WorkflowError value) { this.errors.add(value); }
    public void addModelUsage(ModelUsage value) { this.modelUsage.add(value); }

    public void step(WorkflowStep step) {
        state = new WorkflowState(state.runId(), state.tenant(), state.asOfDate(), step, state.tasks(), state.investigationSteps(),
                state.maxInvestigationSteps(), state.correctionCycles(), state.maxCorrectionCycles(), state.toolCalls(), state.maxToolCalls());
    }

    public void tasks(List<InvestigationTask> tasks) {
        state = new WorkflowState(state.runId(), state.tenant(), state.asOfDate(), state.step(), tasks, state.investigationSteps(),
                state.maxInvestigationSteps(), state.correctionCycles(), state.maxCorrectionCycles(), state.toolCalls(), state.maxToolCalls());
    }

    /** Returns false when the tool-call budget is exhausted; the caller must stop instead of calling. */
    public boolean tryConsumeToolCall() {
        if (state.toolCalls() >= state.maxToolCalls()) {
            return false;
        }
        state = new WorkflowState(state.runId(), state.tenant(), state.asOfDate(), state.step(), state.tasks(), state.investigationSteps(),
                state.maxInvestigationSteps(), state.correctionCycles(), state.maxCorrectionCycles(), state.toolCalls() + 1, state.maxToolCalls());
        return true;
    }

    public boolean tryConsumeCorrectionCycle() {
        if (state.correctionCycles() >= state.maxCorrectionCycles()) {
            return false;
        }
        state = new WorkflowState(state.runId(), state.tenant(), state.asOfDate(), state.step(), state.tasks(), state.investigationSteps(),
                state.maxInvestigationSteps(), state.correctionCycles() + 1, state.maxCorrectionCycles(), state.toolCalls(), state.maxToolCalls());
        return true;
    }

    /** Records the deepest investigation loop used by any task; bounded by maxInvestigationSteps. */
    public void recordInvestigationDepth(int steps) {
        int depth = Math.min(Math.max(state.investigationSteps(), steps), state.maxInvestigationSteps());
        state = new WorkflowState(state.runId(), state.tenant(), state.asOfDate(), state.step(), state.tasks(), depth,
                state.maxInvestigationSteps(), state.correctionCycles(), state.maxCorrectionCycles(), state.toolCalls(), state.maxToolCalls());
    }

    public long elapsedMs() {
        return java.time.Duration.between(startedAt, Instant.now()).toMillis();
    }
}
