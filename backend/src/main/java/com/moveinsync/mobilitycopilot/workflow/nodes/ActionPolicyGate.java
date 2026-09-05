package com.moveinsync.mobilitycopilot.workflow.nodes;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.workflow.domain.Critique;
import com.moveinsync.mobilitycopilot.workflow.domain.PolicyDecision;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Node 15: deterministic allowlist, scope, evidence, expiry and confidence checks before any approval request. */
public final class ActionPolicyGate {

    static final Set<ActionType> ALLOWED = EnumSet.allOf(ActionType.class);
    static final BigDecimal MINIMUM_CONFIDENCE = new BigDecimal("0.40");

    private ActionPolicyGate() {
    }

    public static PolicyDecision evaluate(WorkflowRun run, ActionProposal proposal, Instant now) {
        List<String> reasons = new ArrayList<>();
        if (run.selectedIssue() == null || !run.selectedIssue().material()) {
            return new PolicyDecision(PolicyDecision.Route.REPORT_ONLY, List.of("No material operational anomaly"));
        }
        if (run.critique() != null && run.critique().verdict() == Critique.Verdict.ABSTAIN) {
            return new PolicyDecision(PolicyDecision.Route.REPORT_ONLY, List.of("Evidence critic abstained; report only"));
        }
        if (run.verification() != null && !run.verification().passed()) {
            return new PolicyDecision(PolicyDecision.Route.REPORT_ONLY, List.of("Verification did not pass after the allowed correction cycle"));
        }
        if (run.verification() != null && run.verification().confidence().compareTo(MINIMUM_CONFIDENCE) < 0) {
            return new PolicyDecision(PolicyDecision.Route.REPORT_ONLY, List.of("Confidence " + run.verification().confidence() + " below " + MINIMUM_CONFIDENCE));
        }
        List<String> rejections = validateProposal(run, proposal, now);
        if (!rejections.isEmpty()) {
            return new PolicyDecision(PolicyDecision.Route.REJECTED, rejections);
        }
        reasons.add("Action type " + proposal.type() + " is allowlisted");
        reasons.add("Scope bound to tenant " + run.state().tenant().businessUnit());
        reasons.add("Evidence version " + proposal.evidenceVersion() + " matches the verified package");
        return new PolicyDecision(PolicyDecision.Route.APPROVAL_REQUIRED, reasons);
    }

    /** Re-run on resume and for edited proposals. */
    public static List<String> validateProposal(WorkflowRun run, ActionProposal proposal, Instant now) {
        List<String> rejections = new ArrayList<>();
        if (!ALLOWED.contains(proposal.type())) {
            rejections.add("Action type not allowlisted: " + proposal.type());
        }
        if (!run.state().tenant().businessUnit().equals(proposal.scope().get("businessUnit"))) {
            rejections.add("Action scope is not bound to the authorized business unit");
        }
        if (!proposal.runId().equals(run.state().runId())) {
            rejections.add("Action belongs to another run");
        }
        if (run.evidence() != null && !run.evidence().evidenceVersion().equals(proposal.evidenceVersion())) {
            rejections.add("Action evidence version " + proposal.evidenceVersion() + " does not match verified evidence " + run.evidence().evidenceVersion());
        }
        if (!proposal.expiresAt().isAfter(now)) {
            rejections.add("Action proposal has expired");
        }
        if (proposal.status() != ActionStatus.DRAFT_REQUIRES_APPROVAL) {
            rejections.add("Only draft proposals can be gated");
        }
        return rejections;
    }
}
