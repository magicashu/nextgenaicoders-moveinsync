package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.workflow.domain.*;

/** WS2: implement bounded planning, required comparisons and deterministic fallback. */
public interface SupervisorAgent {
    InvestigationPlan plan(RunContext context, String issueId);
}
