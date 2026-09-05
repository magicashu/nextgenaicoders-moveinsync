package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.workflow.domain.*;

/** WS2: implement the four-stage loop and bounded execution of the seven workers. */
public interface InvestigationAgent {
    InvestigationResult investigate(RunContext context, InvestigationPlan plan);
}
