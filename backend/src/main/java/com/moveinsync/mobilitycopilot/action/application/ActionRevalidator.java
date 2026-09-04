package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;

public interface ActionRevalidator {

    RevalidationResult revalidate(ActionExecutionCommand command);
}
