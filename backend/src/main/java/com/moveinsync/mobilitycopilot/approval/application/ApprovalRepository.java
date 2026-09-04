package com.moveinsync.mobilitycopilot.approval.application;

import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for approval requests, statuses and decisions. Extends the frozen store with lookups the lifecycle needs. */
public interface ApprovalRepository extends ApprovalStore {

    Optional<ApprovalRecord> findRecord(UUID approvalId);

    Optional<ApprovalRecord> findByActionId(UUID actionId);

    List<ApprovalRecord> findByRunId(UUID runId);

    List<ApprovalRecord> findPending(String businessUnit);

    /** Marks a pending request expired (idempotent). */
    ApprovalRecord expire(UUID approvalId, java.time.Instant now);
}
