package com.moveinsync.mobilitycopilot.workflow.investigation;

import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;

public interface InvestigationTool<T> {

    String name();

    // WS2 validates registered worker and scoped requests before invoking the WS1 tool.
    T execute(RunContext context, InvestigationTask task);
}
