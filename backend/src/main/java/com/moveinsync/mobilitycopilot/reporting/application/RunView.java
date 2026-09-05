package com.moveinsync.mobilitycopilot.reporting.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read model of one decision run as the API needs it. Produced by the workflow through
 * {@link DecisionRunGateway}; the API never recomputes a number and never sees raw rows.
 * Shapes mirror the workflow workstream's WorkflowRun/BriefingOutput/EvidencePackage so the
 * composition adapter is a straight mapping.
 */
public record RunView(
        UUID runId,
        String traceId,
        String businessUnit,
        String persona,
        String mode,
        String finalStep,
        Instant startedAt,
        long elapsedMs,
        DecisionBrief brief,
        List<String> operationsBrief,
        List<String> leadershipNarrative,
        List<Claim> claims,
        List<String> capabilityGaps,
        List<String> dataQualityNotes,
        Map<String, String> branchStatus,
        String evidenceVersion,
        Verification verification,
        ActionProposal recommendedAction,
        ApprovalRequest approvalRequest,
        String approvalStatus,
        ExecutionReceipt receipt,
        List<Transition> transitions,
        ModelUsageSummary modelUsage,
        Versions versions,
        List<String> errors) {

    public RunView {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(traceId);
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(finalStep);
        Objects.requireNonNull(brief);
        operationsBrief = operationsBrief == null ? List.of() : List.copyOf(operationsBrief);
        leadershipNarrative = leadershipNarrative == null ? List.of() : List.copyOf(leadershipNarrative);
        claims = claims == null ? List.of() : List.copyOf(claims);
        capabilityGaps = capabilityGaps == null ? List.of() : List.copyOf(capabilityGaps);
        dataQualityNotes = dataQualityNotes == null ? List.of() : List.copyOf(dataQualityNotes);
        branchStatus = branchStatus == null ? Map.of() : Map.copyOf(branchStatus);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public record Claim(String claimId, String text, String kind, List<String> evidenceIds, String worker) {
    }

    public record Verification(boolean passed, BigDecimal confidence, List<String> components, List<String> violations, int correctionCycles) {
    }

    public record Transition(String node, String subNode, String fromStep, String toStep, Instant startedAt, long durationMs, String outcome, Map<String, String> attributes) {
    }

    public record ModelUsageSummary(int modelCalls, int fallbackCalls, long inputTokens, long outputTokens, long modelLatencyMs, String modelId) {
    }

    public record Versions(String workflowVersion, String promptVersion, String contractVersion, String dataVersion, String ruleVersion, String targetVersion) {
    }
}
