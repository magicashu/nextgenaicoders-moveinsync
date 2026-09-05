package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;

/** WS3: must reject expired permits and atomically prevent duplicate effects across retries. */
public interface ActionExecutor {
    ExecutionReceipt executeMock(RunContext context, ExecutionPermit permit, String idempotencyKey);
}
