package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionRecord;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes exactly one mock effect per idempotency key. The repository claim is the boundary: a
 * duplicate approval/resume/retry finds the existing row and receives the same receipt. A failed
 * adapter leaves APPROVED_NOT_EXECUTED with the error, which is retryable only because mock adapters
 * guarantee they produced no effect when they threw.
 */
@Service
public class IdempotentActionExecutor implements ActionExecutor {

    private final ActionExecutionRepository repository;
    private final Map<ActionType, MockActionAdapter> adapters = new EnumMap<>(ActionType.class);
    private final AuditSink audit;

    public IdempotentActionExecutor(ActionExecutionRepository repository, List<MockActionAdapter> adapters, AuditSink audit) {
        this.repository = repository;
        this.audit = audit;
        for (MockActionAdapter adapter : adapters) {
            this.adapters.put(adapter.supports(), adapter);
        }
    }

    @Override
    public ExecutionReceipt execute(ActionExecutionCommand command, RevalidationResult revalidation) {
        if (revalidation == null || !revalidation.valid()) {
            throw new IllegalStateException("Execution requires a valid revalidation result");
        }
        Instant now = command.requestedAt();
        ActionExecutionRecord candidate = new ActionExecutionRecord(command.idempotencyKey(), command.proposal().actionId(), command.proposal().runId(),
                command.tenant().businessUnit(), command.proposal().type(), ActionStatus.EXECUTING, command.expectedEvidenceVersion(), now,
                revalidation.revalidatedAt(), null, null, null, 1);
        ActionExecutionRepository.ClaimResult claim = repository.claim(candidate);
        if (!claim.claimedNow()) {
            ActionExecutionRecord existing = claim.record();
            if (existing.status() == ActionStatus.EXECUTING) {
                // another caller holds the claim: wait briefly for its terminal receipt instead of racing it
                existing = awaitCompletion(command.idempotencyKey(), existing);
            }
            if (existing.status() == ActionStatus.EXECUTED || existing.status() == ActionStatus.EXECUTING) {
                audit.append(event(command, "ACTION_DUPLICATE_SUPPRESSED", Map.of("idempotencyKey", command.idempotencyKey(), "status", existing.status().name())));
                return existing.toReceipt();
            }
            // previous attempt produced no effect: allow a bounded retry under the same key
            if (existing.attempts() >= 3) {
                return existing.toReceipt();
            }
        }
        MockActionAdapter adapter = adapters.get(command.proposal().type());
        if (adapter == null) {
            ActionExecutionRecord failed = repository.complete(command.idempotencyKey(), ActionStatus.APPROVED_NOT_EXECUTED, now, null,
                    "No adapter registered for " + command.proposal().type());
            return failed.toReceipt();
        }
        try {
            String reference = adapter.perform(command.proposal(), command.idempotencyKey());
            ActionExecutionRecord done = repository.complete(command.idempotencyKey(), ActionStatus.EXECUTED, Instant.now(), reference,
                    "Mock " + command.proposal().type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " created");
            audit.append(event(command, "ACTION_EFFECT_RECORDED", Map.of("idempotencyKey", command.idempotencyKey(), "externalReference", reference)));
            return done.toReceipt();
        } catch (RuntimeException adapterFailure) {
            ActionExecutionRecord failed = repository.complete(command.idempotencyKey(), ActionStatus.APPROVED_NOT_EXECUTED, Instant.now(), null,
                    "Adapter failure: " + adapterFailure.getMessage());
            audit.append(event(command, "ACTION_EFFECT_FAILED", Map.of("idempotencyKey", command.idempotencyKey(), "error", String.valueOf(adapterFailure.getMessage()))));
            return failed.toReceipt();
        }
    }

    /** Bounded wait (2 s) for an in-flight execution under the same key; returns the latest record either way. */
    private ActionExecutionRecord awaitCompletion(String idempotencyKey, ActionExecutionRecord current) {
        long deadline = System.currentTimeMillis() + 2_000;
        ActionExecutionRecord latest = current;
        while (latest.status() == ActionStatus.EXECUTING && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            latest = repository.find(idempotencyKey).orElse(latest);
        }
        return latest;
    }

    private static AuditEvent event(ActionExecutionCommand command, String type, Map<String, String> payload) {
        Map<String, String> safe = new java.util.LinkedHashMap<>(payload);
        safe.put("actor", command.actor().actorId());
        safe.put("actionId", command.proposal().actionId().toString());
        safe.put("actionType", command.proposal().type().name());
        safe.put("evidenceVersion", command.expectedEvidenceVersion());
        return new AuditEvent(UUID.randomUUID(), command.proposal().runId(), command.tenant().businessUnit(), type, safe, Instant.now(), command.proposal().runId().toString());
    }
}
