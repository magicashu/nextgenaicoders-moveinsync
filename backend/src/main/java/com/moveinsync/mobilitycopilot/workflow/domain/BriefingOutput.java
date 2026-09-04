package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;

import java.util.List;
import java.util.Objects;

/**
 * Both audience outputs from one verified evidence bundle. Every sentence in either output is a
 * verified claim or a caveat; the leadership narrative may not introduce a fact absent from the brief.
 */
public record BriefingOutput(
        DecisionBrief decisionBrief,
        List<String> operationsBrief,
        List<String> leadershipNarrative,
        ActionProposal recommendedAction,
        String recommendationRationale,
        boolean modelAssisted) {

    public BriefingOutput {
        Objects.requireNonNull(decisionBrief);
        operationsBrief = operationsBrief == null ? List.of() : List.copyOf(operationsBrief);
        leadershipNarrative = leadershipNarrative == null ? List.of() : List.copyOf(leadershipNarrative);
        recommendationRationale = recommendationRationale == null ? "" : recommendationRationale;
    }
}
