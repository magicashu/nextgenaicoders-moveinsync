package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import java.util.List;
import java.util.UUID;

/** WS3: validate scope/version/citation references and persist with optimistic concurrency. */
public record WorkflowCheckpoint(RunContext context, long version, WorkflowNode node,
                                 Status status, List<MetricEvidence> evidence,
                                 List<VerifiedClaim> claims, UUID pendingActionId,
                                 List<String> warnings) {
    public enum Status { RUNNING, PARTIAL, AWAITING_APPROVAL, COMPLETED, FAILED, CANCELLED }
}
