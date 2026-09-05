package com.moveinsync.mobilitycopilot.approval.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** WS3: authenticate approver, match exact proposal/evidence versions and record decisions atomically. */
public record ApprovalDecision(UUID approvalId, UUID actionId, UUID runId, TenantContext tenant,
                               long proposalVersion, String dataVersion, String metricVersion,
                               Set<String> evidenceIds, Type decision, String decidedBy,
                               Instant decidedAt, String comment) {
    public enum Type { APPROVE, REJECT, CANCEL }
}
