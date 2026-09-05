package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionRecord;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency boundary. {@link #claim} atomically inserts the key in EXECUTING state or returns the
 * existing row, so two concurrent approvals of the same action can never both perform the effect.
 */
public interface ActionExecutionRepository {

    /** Returns the existing record when the key is known; otherwise inserts and returns a fresh EXECUTING record. */
    ClaimResult claim(ActionExecutionRecord candidate);

    ActionExecutionRecord complete(String idempotencyKey, ActionStatus status, Instant at, String externalReference, String message);

    Optional<ActionExecutionRecord> find(String idempotencyKey);

    Optional<ActionExecutionRecord> findByActionId(UUID actionId);

    record ClaimResult(ActionExecutionRecord record, boolean claimedNow) {
    }
}
