package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowEngine;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import org.springframework.stereotype.Service;

/** WS2: implement the eighteen-node workflow; WS3 supplies authorization and durable control ports. */
@Service
public final class DeterministicWorkflowEngine implements WorkflowEngine {
    @Override
    public WorkflowCheckpoint start(RunContext context) {
        throw new UnsupportedOperationException("TODO WS2: implement the governed eighteen-node workflow");
    }

    @Override
    public WorkflowCheckpoint resume(RunContext context, ApprovalDecision decision) {
        throw new UnsupportedOperationException("TODO WS2/WS3: implement approval, fresh revalidation and resume");
    }
}
