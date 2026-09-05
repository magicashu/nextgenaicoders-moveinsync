package com.moveinsync.mobilitycopilot.approval.application;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalStatus;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalTransitionException;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic approval state machine: PENDING -> APPROVED | REJECTED | EDITED | EXPIRED.
 * The deciding actor must belong to the request's tenant and hold APPROVE_ACTION; edits may narrow
 * scope but never change tenant, evidence version or upgrade to a vendor escalation. Every transition
 * is audited before it is returned.
 */
@Service
public class ApprovalLifecycle {

    private final ApprovalRepository repository;
    private final AccessAuthorizer authorizer;
    private final AuditSink audit;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalLifecycle(ApprovalRepository repository, AccessAuthorizer authorizer, AuditSink audit) {
        this(repository, authorizer, audit, Clock.systemUTC());
    }

    public ApprovalLifecycle(ApprovalRepository repository, AccessAuthorizer authorizer, AuditSink audit, Clock clock) {
        this.repository = repository;
        this.authorizer = authorizer;
        this.audit = audit;
        this.clock = clock;
    }

    public ApprovalRecord request(ApprovalRequest request, String traceId) {
        repository.create(request);
        ApprovalRecord record = repository.findRecord(request.approvalId()).orElseThrow();
        audit.append(event(request.runId(), request.businessUnit(), "APPROVAL_REQUESTED", traceId, Map.of(
                "approvalId", request.approvalId().toString(), "actionId", request.proposal().actionId().toString(),
                "actionType", request.proposal().type().name(), "evidenceVersion", request.evidenceVersion(), "expiresAt", request.expiresAt().toString())));
        return record;
    }

    /** Applies approve, reject or edit. Expiry is applied lazily whenever a decision arrives late. */
    public ApprovalRecord decide(ActorContext actor, UUID approvalId, ApprovalDecisionType type, String comment, ActionProposal editedProposal, String traceId) {
        ApprovalDecision decision = prepareDecision(actor, approvalId, type, comment, editedProposal, traceId);
        repository.decide(decision);
        ApprovalRecord updated = repository.findRecord(approvalId).orElseThrow();
        auditDecision(updated.request(), decision, traceId);
        return updated;
    }

    /**
     * Validates and constructs a decision without persisting it. The resumable workflow uses this
     * path because its revalidation node owns the atomic approval transition and action execution.
     */
    public ApprovalDecision prepareDecision(ActorContext actor, UUID approvalId, ApprovalDecisionType type,
                                            String comment, ActionProposal editedProposal, String traceId) {
        ApprovalRecord record = repository.findRecord(approvalId)
                .orElseThrow(() -> new ApprovalTransitionException("UNKNOWN_APPROVAL", "Approval request does not exist"));
        ApprovalRequest request = record.request();
        TenantContext tenant = new TenantContext(request.businessUnit());
        authorizer.require(actor, tenant, Permission.APPROVE_ACTION);
        Instant now = clock.instant();
        if (record.status() != ApprovalStatus.PENDING) {
            throw new ApprovalTransitionException("ALREADY_DECIDED", "Approval is already " + record.status());
        }
        if (!request.expiresAt().isAfter(now)) {
            ApprovalRecord expired = repository.expire(approvalId, now);
            audit.append(event(request.runId(), request.businessUnit(), "APPROVAL_EXPIRED", traceId, Map.of(
                    "approvalId", approvalId.toString(), "decidedBy", actor.actorId(), "attemptedDecision", type.name())));
            throw new ApprovalTransitionException("EXPIRED", "Approval expired at " + request.expiresAt() + "; status is now " + expired.status());
        }
        ActionProposal proposal = null;
        if (type == ApprovalDecisionType.EDIT) {
            proposal = validateEdit(request, editedProposal);
        }
        return new ApprovalDecision(approvalId, request.proposal().actionId(), request.runId(), type, actor.actorId(), now, comment, proposal);
    }

    private void auditDecision(ApprovalRequest request, ApprovalDecision decision, String traceId) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("approvalId", decision.approvalId().toString());
        payload.put("actionId", request.proposal().actionId().toString());
        payload.put("decision", decision.decision().name());
        payload.put("decidedBy", decision.decidedBy());
        payload.put("evidenceVersion", request.evidenceVersion());
        payload.put("comment", decision.comment() == null ? "" : decision.comment());
        if (decision.editedProposal() != null) {
            payload.put("editedScope", decision.editedProposal().scope().toString());
        }
        audit.append(event(request.runId(), request.businessUnit(), "APPROVAL_" + decision.decision().name(), traceId, payload));
    }

    /** Sweeps pending requests past their expiry; safe to call repeatedly. */
    public int expirePending(String businessUnit, String traceId) {
        Instant now = clock.instant();
        int expired = 0;
        for (ApprovalRecord record : repository.findPending(businessUnit)) {
            if (!record.request().expiresAt().isAfter(now)) {
                repository.expire(record.request().approvalId(), now);
                audit.append(event(record.request().runId(), businessUnit, "APPROVAL_EXPIRED", traceId,
                        Map.of("approvalId", record.request().approvalId().toString(), "expiresAt", record.request().expiresAt().toString())));
                expired++;
            }
        }
        return expired;
    }

    static ActionProposal validateEdit(ApprovalRequest request, ActionProposal edited) {
        if (edited == null) {
            throw new ApprovalTransitionException("EDIT_REQUIRES_PROPOSAL", "EDIT requires the edited proposal");
        }
        ActionProposal original = request.proposal();
        if (!edited.actionId().equals(original.actionId()) || !edited.runId().equals(original.runId())) {
            throw new ApprovalTransitionException("EDIT_IDENTITY", "Edited proposal must keep the action and run identifiers");
        }
        if (!edited.evidenceVersion().equals(original.evidenceVersion())) {
            throw new ApprovalTransitionException("EDIT_EVIDENCE", "Edited proposal must reference the same evidence version");
        }
        if (!request.businessUnit().equals(edited.scope().get("businessUnit"))) {
            throw new ApprovalTransitionException("EDIT_TENANT", "Edited scope must stay inside the authorized business unit");
        }
        if (edited.type() != original.type() && edited.type() == ActionType.DRAFT_VENDOR_ESCALATION) {
            throw new ApprovalTransitionException("EDIT_ESCALATION", "An edit may not upgrade the action to a vendor escalation");
        }
        if (edited.status() != ActionStatus.DRAFT_REQUIRES_APPROVAL) {
            throw new ApprovalTransitionException("EDIT_STATUS", "Edited proposal must remain a draft");
        }
        if (edited.expiresAt().isAfter(original.expiresAt())) {
            throw new ApprovalTransitionException("EDIT_EXPIRY", "An edit may not extend the expiry");
        }
        return edited;
    }

    private static AuditEvent event(UUID runId, String businessUnit, String type, String traceId, Map<String, String> payload) {
        return new AuditEvent(UUID.randomUUID(), runId, businessUnit, type, payload, Instant.now(), traceId == null ? runId.toString() : traceId);
    }
}
