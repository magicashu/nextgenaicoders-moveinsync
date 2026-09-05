package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.AccessDeniedException;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionRecord;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Post-approval revalidation (node 17 precondition): authorization, approval state, evidence version,
 * expiry and current action state are re-checked at execution time, never trusted from the request.
 */
@Service
public class DeterministicActionRevalidator implements ActionRevalidator {

    private final AccessAuthorizer authorizer;
    private final ApprovalRepository approvals;
    private final ActionExecutionRepository executions;

    public DeterministicActionRevalidator(AccessAuthorizer authorizer, ApprovalRepository approvals, ActionExecutionRepository executions) {
        this.authorizer = authorizer;
        this.approvals = approvals;
        this.executions = executions;
    }

    @Override
    public RevalidationResult revalidate(ActionExecutionCommand command) {
        List<String> reasons = new ArrayList<>();
        try {
            authorizer.require(command.actor(), command.tenant(), Permission.APPROVE_ACTION);
        } catch (AccessDeniedException denied) {
            reasons.add("authorization: " + denied.code());
        }
        if (!command.proposal().scope().getOrDefault("businessUnit", command.tenant().businessUnit()).equals(command.tenant().businessUnit())) {
            reasons.add("scope: proposal scope is outside the authorized business unit");
        }
        if (!command.proposal().expiresAt().isAfter(command.requestedAt())) {
            reasons.add("expiry: proposal expired at " + command.proposal().expiresAt());
        }
        Optional<ApprovalRecord> approval = approvals.findByActionId(command.proposal().actionId());
        if (approval.isEmpty()) {
            reasons.add("approval: no approval request exists for this action");
        } else {
            ApprovalRecord record = approval.get();
            if (record.status() != ApprovalStatus.APPROVED && record.status() != ApprovalStatus.EDITED) {
                reasons.add("approval: status is " + record.status() + ", execution requires APPROVED or EDITED");
            }
            if (!record.request().evidenceVersion().equals(command.expectedEvidenceVersion())) {
                reasons.add("evidence: approval was granted on " + record.request().evidenceVersion() + " but execution expects " + command.expectedEvidenceVersion());
            }
            if (!record.request().businessUnit().equals(command.tenant().businessUnit())) {
                reasons.add("tenant: approval belongs to another business unit");
            }
            if (record.status() == ApprovalStatus.EDITED && record.decision() != null && record.decision().editedProposal() != null
                    && !record.decision().editedProposal().scope().equals(command.proposal().scope())) {
                reasons.add("edit: executing proposal does not match the approved edit");
            }
        }
        Optional<ActionExecutionRecord> existing = executions.findByActionId(command.proposal().actionId());
        if (existing.isPresent() && existing.get().status() == ActionStatus.EXECUTED && !existing.get().idempotencyKey().equals(command.idempotencyKey())) {
            reasons.add("state: action already executed under key " + existing.get().idempotencyKey());
        }
        return new RevalidationResult(reasons.isEmpty(), command.expectedEvidenceVersion(), command.requestedAt(), reasons);
    }
}
