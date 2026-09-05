package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyIssue;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.Objects;

/** Node-8 input. Detector/capability outputs are trusted; userContext remains untrusted data. */
public record SupervisorPlanningRequest(RunContext context, AnomalyIssue issue,
                                        CapabilityMatrix capabilities, String userContext) {
    public SupervisorPlanningRequest(RunContext context, AnomalyIssue issue,
                                     CapabilityMatrix capabilities) {
        this(context, issue, capabilities, "");
    }

    public SupervisorPlanningRequest {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(issue, "issue is required");
        Objects.requireNonNull(capabilities, "capabilities are required");
        if (userContext == null) userContext = "";
        if (userContext.length() > 32_000) {
            throw new IllegalArgumentException("user context exceeds 32000 characters");
        }
    }
}
