package com.moveinsync.mobilitycopilot.reporting.adapter;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.reporting.application.ApprovalNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway over the scaffold coordinator so this branch boots and the API can be exercised end to end.
 * It keeps runs and approvals in memory and performs NO side effect on approval (receipt says so). The
 * composition root replaces it with the workflow adapter ({@code mobility.api.gateway=workflow}).
 */
@Component
@ConditionalOnProperty(name = "mobility.api.gateway", havingValue = "scaffold", matchIfMissing = true)
public class ScaffoldDecisionRunGateway implements DecisionRunGateway {

    private final WorkflowCoordinator coordinator;
    private final AccessAuthorizer authorizer;
    private final AuditSink audit;
    private final Map<UUID, RunView> runs = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> approvalToRun = new ConcurrentHashMap<>();

    public ScaffoldDecisionRunGateway(WorkflowCoordinator coordinator, AccessAuthorizer authorizer, AuditSink audit) {
        this.coordinator = coordinator;
        this.authorizer = authorizer;
        this.audit = audit;
    }

    @Override
    public RunView morningBrief(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona) {
        authorizer.require(actor, tenant, Permission.READ_TENANT_METRICS);
        Instant started = Instant.now();
        DecisionBrief brief = coordinator.createDemoBrief(tenant.businessUnit(), asOfDate);
        ActionProposal action = brief.recommendedAction();
        ApprovalRequest approval = "AWAITING_APPROVAL".equals(brief.status())
                ? new ApprovalRequest(UUID.randomUUID(), brief.runId(), tenant.businessUnit(), action, action.evidenceVersion(), action.createdAt(), action.expiresAt()) : null;
        List<RunView.Claim> claims = new ArrayList<>();
        String headlineEvidence = brief.evidence().items().getFirst().evidenceId();
        for (int i = 0; i < brief.findings().size(); i++) {
            claims.add(new RunView.Claim("c" + (i + 1), brief.findings().get(i), "DIRECT", List.of(headlineEvidence), "workflow"));
        }
        for (String caveat : brief.evidence().caveats()) {
            claims.add(new RunView.Claim("caveat-" + claims.size(), caveat, "CAVEAT", List.of(headlineEvidence), "system"));
        }
        RunView view = new RunView(brief.runId(), brief.runId().toString(), tenant.businessUnit(), persona, "ON_DEMAND", brief.status(), started,
                java.time.Duration.between(started, Instant.now()).toMillis(), brief, brief.findings(), List.of(brief.headline() + ".", "Recommended: " + action.title() + " (awaiting approval)."),
                claims, List.of("Scaffold gateway: only M01 is available on this seam"), List.of(), Map.of("headline", "COMPLETE"), action.evidenceVersion(),
                new RunView.Verification(true, java.math.BigDecimal.valueOf(brief.evidence().confidence()), List.of("scaffold"), List.of(), 0), action, approval,
                approval == null ? null : "PENDING", null, List.of(), new RunView.ModelUsageSummary(0, 0, 0, 0, 0, "none"),
                new RunView.Versions("workflow-v1", "prompts-v1", brief.metric().contractVersion(), brief.metric().dataVersion(), "anomaly-rules-v1", "targets-v1"), List.of());
        runs.put(brief.runId(), view);
        if (approval != null) {
            approvalToRun.put(approval.approvalId(), brief.runId());
        }
        audit.append(new AuditEvent(UUID.randomUUID(), brief.runId(), tenant.businessUnit(), "BRIEF_CREATED", Map.of("actor", actor.actorId(), "status", brief.status()), Instant.now(), view.traceId()));
        if (approval != null) {
            audit.append(new AuditEvent(UUID.randomUUID(), brief.runId(), tenant.businessUnit(), "ACTION_AWAITING_APPROVAL", Map.of("approvalId", approval.approvalId().toString(), "actionId", action.actionId().toString()), Instant.now(), view.traceId()));
        }
        return view;
    }

    @Override
    public RunView ask(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona, String question, UUID relatedRunId) {
        return morningBrief(actor, tenant, asOfDate, persona);
    }

    @Override
    public Optional<RunView> find(ActorContext actor, UUID runId) {
        return Optional.ofNullable(runs.get(runId)).filter(r -> r.businessUnit().equals(actor.businessUnit()));
    }

    @Override
    public RunView decide(ActorContext actor, UUID approvalId, ApprovalDecisionType decision, String comment, ActionProposal editedProposal) {
        RunView run = findByApproval(actor, approvalId).orElseThrow(() -> new ApprovalNotFoundException(approvalId));
        authorizer.require(actor, new TenantContext(run.businessUnit()), Permission.APPROVE_ACTION);
        if (!"PENDING".equals(run.approvalStatus())) {
            throw new IllegalStateException("Approval is already " + run.approvalStatus());
        }
        String status = switch (decision) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case EDIT -> "EDITED";
        };
        ExecutionReceipt receipt = decision == ApprovalDecisionType.REJECT ? null : new ExecutionReceipt(run.recommendedAction().actionId(), run.runId(),
                run.runId() + ":" + run.recommendedAction().actionId(), ActionStatus.APPROVED_NOT_EXECUTED, Instant.now(), null, null,
                "Scaffold gateway performs no side effect; the workflow adapter executes through the governed lifecycle");
        String finalStep = decision == ApprovalDecisionType.REJECT ? "REJECTED" : "APPROVED_NOT_EXECUTED";
        RunView updated = new RunView(run.runId(), run.traceId(), run.businessUnit(), run.persona(), "RESUME", finalStep, run.startedAt(), run.elapsedMs(), run.brief(),
                run.operationsBrief(), run.leadershipNarrative(), run.claims(), run.capabilityGaps(), run.dataQualityNotes(), run.branchStatus(), run.evidenceVersion(),
                run.verification(), editedProposal == null ? run.recommendedAction() : editedProposal, run.approvalRequest(), status, receipt,
                List.of(new RunView.Transition("REVALIDATE_AND_EXECUTE", null, "AWAITING_APPROVAL", finalStep, Instant.now(), 0, "scaffold", Map.of("result", finalStep))),
                run.modelUsage(), run.versions(), run.errors());
        runs.put(run.runId(), updated);
        audit.append(new AuditEvent(UUID.randomUUID(), run.runId(), run.businessUnit(), "APPROVAL_" + decision.name(), Map.of("actor", actor.actorId(), "approvalId", approvalId.toString(), "comment", comment == null ? "" : comment), Instant.now(), run.traceId()));
        return updated;
    }

    @Override
    public Optional<RunView> findByApproval(ActorContext actor, UUID approvalId) {
        UUID runId = approvalToRun.get(approvalId);
        return runId == null ? Optional.empty() : find(actor, runId);
    }
}
