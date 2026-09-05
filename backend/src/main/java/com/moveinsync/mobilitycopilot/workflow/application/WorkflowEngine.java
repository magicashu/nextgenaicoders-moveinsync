package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.workflow.domain.*;

/** Shared contract frozen before team implementation. No runtime completion is implied. */
public interface WorkflowEngine {
    WorkflowCheckpoint start(RunContext context);
    WorkflowCheckpoint resume(RunContext context, ApprovalDecision decision);
}
