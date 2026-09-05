package com.moveinsync.mobilitycopilot.observability;

import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import java.time.Duration;

/** WS6: implement correlated, redacted diagnostic telemetry separately from business audit. */
public interface TraceSink {
    void recordNode(RunContext context, WorkflowNode node, String status, Duration elapsed);
}
