package com.moveinsync.mobilitycopilot.action.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import java.time.Instant;
import java.util.UUID;

/** WS3: populate from an idempotent mock effect and audit event, never from incident resolution. */
public record ExecutionReceipt(UUID actionId, UUID runId, TenantContext tenant,
                               UUID approvalId, long proposalVersion, String idempotencyKey,
                               UUID auditId, Instant executedAt, Status status) {
    public enum Status { MOCK_EXECUTED, DUPLICATE_PREVENTED }
}
