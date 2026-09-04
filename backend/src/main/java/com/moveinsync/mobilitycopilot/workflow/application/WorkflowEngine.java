package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;

public interface WorkflowEngine {

    WorkflowOutcome run(WorkflowState initialState);
}
