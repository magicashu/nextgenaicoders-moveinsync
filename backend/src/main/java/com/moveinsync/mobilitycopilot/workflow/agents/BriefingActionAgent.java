package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;

/** WS2/WS4: render the same verified facts for operations/leadership; WS3 owns policy and effects. */
public interface BriefingActionAgent {
    DecisionBrief draft(RunContext context, VerificationResult verifiedEvidence);
}
