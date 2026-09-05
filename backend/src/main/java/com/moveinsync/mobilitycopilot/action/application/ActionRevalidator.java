package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;

/** WS3: resolve fresh evidence and validate the entire exact proposal before issuing a permit. */
public interface ActionRevalidator {
    ExecutionPermit revalidate(RunContext context, ActionProposal proposal, ApprovalDecision approval);
}
