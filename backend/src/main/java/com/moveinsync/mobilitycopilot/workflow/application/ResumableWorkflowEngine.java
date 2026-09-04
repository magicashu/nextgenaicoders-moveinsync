package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;

import java.util.Optional;
import java.util.UUID;

/** Additive extension of the frozen {@link WorkflowEngine}: explicit request context and approval resume. */
public interface ResumableWorkflowEngine extends WorkflowEngine {

    WorkflowOutcome run(WorkflowState initialState, RunContext context);

    /** Resumes a paused run after a human decision. Side effects happen only here, after revalidation. */
    WorkflowOutcome resume(ApprovalDecision decision, RunContext context);

    /** In-process view of a run for the API; rebuilt deterministically after a restart on resume. */
    Optional<WorkflowRun> find(UUID runId);
}
