package com.moveinsync.mobilitycopilot.reporting.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The application interface the REST layer depends on. Implementations delegate to the workflow
 * coordinator, approval lifecycle and audit ledger; controllers never touch those directly and never
 * contain metric, workflow or approval logic.
 */
public interface DecisionRunGateway {

    /** Scheduled/on-demand morning brief for the authorized tenant as of a date. */
    RunView morningBrief(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona);

    /** Contextual question through the same workflow, restricted to the tenant and governed tools. */
    RunView ask(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona, String question, UUID relatedRunId);

    Optional<RunView> find(ActorContext actor, UUID runId);

    /** Applies a human decision and resumes the paused run (revalidation and exactly-one execution happen inside). */
    RunView decide(ActorContext actor, UUID approvalId, ApprovalDecisionType decision, String comment, ActionProposal editedProposal);

    /** Approval pending for a run, if any. */
    Optional<RunView> findByApproval(ActorContext actor, UUID approvalId);
}
