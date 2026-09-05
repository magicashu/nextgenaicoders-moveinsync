package com.moveinsync.mobilitycopilot.reporting.domain;

import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.time.Instant;
import java.util.UUID;

/** WS4/WS3: validate lifecycle, capacity, expiry and identity/version-safe reuse. */
public record BriefJob(UUID jobId, RunContext context, Status status,
                       Instant createdAt, Instant expiresAt, String failureReason) {
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
}
