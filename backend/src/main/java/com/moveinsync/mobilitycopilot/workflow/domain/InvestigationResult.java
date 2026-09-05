package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;

import java.util.List;
import java.util.Objects;

/** Result of one isolated investigation task after the four-node subgraph. */
public record InvestigationResult(
        String worker,
        Status status,
        List<WorkerEvidenceDto> evidence,
        List<String> directFindings,
        List<String> inferences,
        List<String> unresolvedQuestions,
        List<String> qualityWarnings,
        int steps,
        int toolCalls,
        long latencyMs,
        String failureReason) {

    public enum Status { COMPLETE, PARTIAL, FAILED, SKIPPED }

    public InvestigationResult {
        Objects.requireNonNull(worker);
        Objects.requireNonNull(status);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        directFindings = directFindings == null ? List.of() : List.copyOf(directFindings);
        inferences = inferences == null ? List.of() : List.copyOf(inferences);
        unresolvedQuestions = unresolvedQuestions == null ? List.of() : List.copyOf(unresolvedQuestions);
        qualityWarnings = qualityWarnings == null ? List.of() : List.copyOf(qualityWarnings);
    }

    public boolean succeeded() {
        return status == Status.COMPLETE || status == Status.PARTIAL;
    }
}
