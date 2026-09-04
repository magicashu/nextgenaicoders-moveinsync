package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;

public interface ActionExecutor {

    ExecutionReceipt execute(ActionExecutionCommand command, RevalidationResult revalidation);
}
