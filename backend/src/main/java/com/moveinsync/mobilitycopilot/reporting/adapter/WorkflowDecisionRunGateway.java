package com.moveinsync.mobilitycopilot.reporting.adapter;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalLifecycle;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.reporting.application.ApprovalNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import com.moveinsync.mobilitycopilot.workflow.domain.BriefingOutput;
import com.moveinsync.mobilitycopilot.workflow.domain.ModelUsage;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Product API adapter over the real resumable workflow and governed approval control plane. */
@Component
@ConditionalOnProperty(name = "mobility.api.gateway", havingValue = "workflow", matchIfMissing = true)
public final class WorkflowDecisionRunGateway implements DecisionRunGateway {

    private final WorkflowCoordinator coordinator;
    private final AccessAuthorizer authorizer;
    private final ApprovalLifecycle approvals;
    private final ApprovalRepository approvalRepository;

    public WorkflowDecisionRunGateway(
            WorkflowCoordinator coordinator,
            AccessAuthorizer authorizer,
            ApprovalLifecycle approvals,
            ApprovalRepository approvalRepository) {
        this.coordinator = coordinator;
        this.authorizer = authorizer;
        this.approvals = approvals;
        this.approvalRepository = approvalRepository;
    }

    @Override
    public RunView morningBrief(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona) {
        authorizer.require(actor, tenant, Permission.READ_TENANT_METRICS);
        WorkflowOutcome outcome = coordinator.run(actor, tenant, asOfDate, persona(persona), RunContext.RequestMode.ON_DEMAND, null);
        return view(requiredRun(outcome.brief().runId()));
    }

    @Override
    public RunView ask(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona, String question, UUID relatedRunId) {
        authorizer.require(actor, tenant, Permission.INVESTIGATE_TENANT);
        if (relatedRunId != null) {
            find(actor, relatedRunId).orElseThrow(() -> new IllegalArgumentException("Related run is unavailable to this actor"));
        }
        WorkflowOutcome outcome = coordinator.run(actor, tenant, asOfDate, persona(persona), RunContext.RequestMode.QUESTION, question);
        return view(requiredRun(outcome.brief().runId()));
    }

    @Override
    public Optional<RunView> find(ActorContext actor, UUID runId) {
        return coordinator.find(runId)
                .filter(run -> run.state().tenant().businessUnit().equals(actor.businessUnit()))
                .map(this::view);
    }

    @Override
    public RunView decide(ActorContext actor, UUID approvalId, ApprovalDecisionType type, String comment, ActionProposal editedProposal) {
        ApprovalRecord record = approvalRepository.findRecord(approvalId)
                .orElseThrow(() -> new ApprovalNotFoundException(approvalId));
        if (!record.request().businessUnit().equals(actor.businessUnit())) {
            // Hide approval existence across tenants; callers receive the same result as an unknown id.
            throw new ApprovalNotFoundException(approvalId);
        }
        String traceId = coordinator.find(record.request().runId())
                .map(run -> run.context().traceId())
                .orElse(record.request().runId().toString());
        ApprovalDecision decision = approvals.prepareDecision(actor, approvalId, type, comment, editedProposal, traceId);
        WorkflowOutcome outcome = coordinator.resume(actor, decision);
        return view(requiredRun(outcome.brief().runId()));
    }

    @Override
    public Optional<RunView> findByApproval(ActorContext actor, UUID approvalId) {
        return approvalRepository.findRecord(approvalId).flatMap(record -> find(actor, record.request().runId()));
    }

    private WorkflowRun requiredRun(UUID runId) {
        return coordinator.find(runId).orElseThrow(() -> new IllegalStateException("Workflow run was not retained: " + runId));
    }

    private RunView view(WorkflowRun run) {
        BriefingOutput briefing = run.briefing();
        EvidencePackage evidence = run.evidence();
        String approvalStatus = run.approvalRequest() == null ? null : approvalRepository
                .findRecord(run.approvalRequest().approvalId()).map(record -> record.status().name()).orElse("UNKNOWN");
        List<RunView.Claim> claims = evidence == null ? List.of() : evidence.claims().stream()
                .map(claim -> new RunView.Claim(claim.claimId(), claim.text(), claim.kind().name(), claim.evidenceIds(), claim.worker()))
                .toList();
        RunView.Verification verification = run.verification() == null ? null : new RunView.Verification(
                run.verification().passed(),
                run.verification().confidence(),
                run.verification().confidenceComponents(),
                run.verification().violations().stream()
                        .map(v -> v.code() + (v.claimId() == null ? "" : ":" + v.claimId()) + ": " + v.message())
                        .toList(),
                run.state().correctionCycles());
        String contractVersion = briefing == null || briefing.decisionBrief().metric() == null
                ? "unknown" : briefing.decisionBrief().metric().contractVersion();
        String dataVersion = run.context().dataVersion();
        String ruleVersion = run.detection() == null ? "unknown" : run.detection().ruleVersion();
        return new RunView(
                run.state().runId(),
                run.context().traceId(),
                run.state().tenant().businessUnit(),
                run.context().persona().name(),
                run.context().mode().name(),
                run.state().step().name(),
                run.startedAt(),
                run.elapsedMs(),
                briefing.decisionBrief(),
                briefing.operationsBrief(),
                briefing.leadershipNarrative(),
                claims,
                evidence == null ? List.of() : evidence.capabilityGaps(),
                evidence == null ? List.of() : evidence.dataQualityNotes(),
                evidence == null ? java.util.Map.of() : evidence.branchStatus(),
                evidence == null ? "unknown" : evidence.evidenceVersion(),
                verification,
                run.action(),
                run.approvalRequest(),
                approvalStatus,
                run.receipt(),
                run.transitions().stream().map(WorkflowDecisionRunGateway::transition).toList(),
                modelUsage(run.modelUsage()),
                new RunView.Versions(run.context().workflowVersion(), run.context().promptVersion(), contractVersion,
                        dataVersion, ruleVersion, "targets-v1"),
                run.errors().stream().map(error -> error.code() + ": " + error.message()).toList());
    }

    private static RunView.Transition transition(TransitionEvent event) {
        return new RunView.Transition(
                event.node().name(),
                event.subNode(),
                event.fromStep() == null ? null : event.fromStep().name(),
                event.toStep() == null ? null : event.toStep().name(),
                event.startedAt(), event.durationMs(), event.outcome(), event.attributes());
    }

    private static RunView.ModelUsageSummary modelUsage(List<ModelUsage> values) {
        return new RunView.ModelUsageSummary(
                (int) values.stream().filter(value -> !value.modelId().equals("none")).count(),
                (int) values.stream().filter(ModelUsage::fallbackUsed).count(),
                values.stream().mapToLong(ModelUsage::inputTokens).sum(),
                values.stream().mapToLong(ModelUsage::outputTokens).sum(),
                values.stream().mapToLong(ModelUsage::latencyMs).sum(),
                values.isEmpty() ? "none" : values.getFirst().modelId());
    }

    private static RunContext.Persona persona(String value) {
        if (value == null || value.isBlank()) {
            return RunContext.Persona.TRANSPORT_MANAGER;
        }
        return RunContext.Persona.valueOf(value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
    }
}
