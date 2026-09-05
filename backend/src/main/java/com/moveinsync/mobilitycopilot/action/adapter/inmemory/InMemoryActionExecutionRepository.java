package com.moveinsync.mobilitycopilot.action.adapter.inmemory;

import com.moveinsync.mobilitycopilot.action.application.ActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionRecord;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionExecutionRepository implements ActionExecutionRepository {

    private final Map<String, ActionExecutionRecord> records = new ConcurrentHashMap<>();

    @Override
    public synchronized ClaimResult claim(ActionExecutionRecord candidate) {
        ActionExecutionRecord existing = records.get(candidate.idempotencyKey());
        if (existing == null) {
            records.put(candidate.idempotencyKey(), candidate);
            return new ClaimResult(candidate, true);
        }
        if (existing.status() == ActionStatus.APPROVED_NOT_EXECUTED) {
            ActionExecutionRecord retry = new ActionExecutionRecord(existing.idempotencyKey(), existing.actionId(), existing.runId(), existing.businessUnit(),
                    existing.type(), ActionStatus.EXECUTING, existing.evidenceVersion(), existing.claimedAt(), candidate.revalidatedAt(), null, null,
                    existing.message(), existing.attempts() + 1);
            records.put(existing.idempotencyKey(), retry);
            return new ClaimResult(new ActionExecutionRecord(existing.idempotencyKey(), existing.actionId(), existing.runId(), existing.businessUnit(),
                    existing.type(), ActionStatus.APPROVED_NOT_EXECUTED, existing.evidenceVersion(), existing.claimedAt(), existing.revalidatedAt(), null, null,
                    existing.message(), existing.attempts()), false);
        }
        return new ClaimResult(existing, false);
    }

    @Override
    public synchronized ActionExecutionRecord complete(String idempotencyKey, ActionStatus status, Instant at, String externalReference, String message) {
        ActionExecutionRecord existing = records.get(idempotencyKey);
        if (existing == null) {
            throw new IllegalStateException("Unknown idempotency key " + idempotencyKey);
        }
        ActionExecutionRecord done = new ActionExecutionRecord(existing.idempotencyKey(), existing.actionId(), existing.runId(), existing.businessUnit(),
                existing.type(), status, existing.evidenceVersion(), existing.claimedAt(), existing.revalidatedAt(),
                status == ActionStatus.EXECUTED ? at : null, externalReference, message, existing.attempts());
        records.put(idempotencyKey, done);
        return done;
    }

    @Override
    public Optional<ActionExecutionRecord> find(String idempotencyKey) {
        return Optional.ofNullable(records.get(idempotencyKey));
    }

    @Override
    public Optional<ActionExecutionRecord> findByActionId(UUID actionId) {
        return records.values().stream().filter(r -> r.actionId().equals(actionId)).findFirst();
    }
}
