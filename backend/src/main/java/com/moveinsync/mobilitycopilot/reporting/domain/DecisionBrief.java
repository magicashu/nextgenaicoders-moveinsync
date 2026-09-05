package com.moveinsync.mobilitycopilot.reporting.domain;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.List;

/** WS4: both summaries use the same verified facts; an empty proposedActions list is valid. */
public record DecisionBrief(
        RunContext context,
        String operationalSummary,
        String leadershipSummary,
        VerificationResult verification,
        List<ActionProposal> proposedActions,
        List<String> caveats) {
}
