package com.moveinsync.mobilitycopilot.audit.adapter.inmemory;

import com.moveinsync.mobilitycopilot.audit.application.AuditLedger;
import com.moveinsync.mobilitycopilot.audit.domain.AuditChain;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.audit.domain.ChainedAuditEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Append-only in-memory ledger with the same hash chain as the PostgreSQL adapter. */
public class InMemoryAuditLedger implements AuditLedger {

    private final List<ChainedAuditEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, String> lastHashByRun = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized AuditEvent append(AuditEvent event) {
        String previous = lastHashByRun.getOrDefault(event.runId(), AuditChain.GENESIS);
        String hash = AuditChain.hash(event, previous);
        events.add(new ChainedAuditEvent(sequence.incrementAndGet(), event, previous, hash));
        lastHashByRun.put(event.runId(), hash);
        return event;
    }

    @Override
    public List<AuditEvent> findByRunId(UUID runId) {
        return events.stream().filter(e -> e.event().runId().equals(runId)).map(ChainedAuditEvent::event).toList();
    }

    @Override
    public List<ChainedAuditEvent> chainForRun(UUID runId) {
        return events.stream().filter(e -> e.event().runId().equals(runId)).toList();
    }

    @Override
    public List<ChainedAuditEvent> recentForBusinessUnit(String businessUnit, int limit) {
        List<ChainedAuditEvent> matching = new ArrayList<>(events.stream().filter(e -> e.event().businessUnit().equals(businessUnit)).toList());
        java.util.Collections.reverse(matching);
        return matching.subList(0, Math.min(limit, matching.size()));
    }

    @Override
    public boolean verifyChain(UUID runId) {
        String previous = AuditChain.GENESIS;
        for (ChainedAuditEvent chained : chainForRun(runId)) {
            if (!chained.previousHash().equals(previous) || !AuditChain.hash(chained.event(), previous).equals(chained.eventHash())) {
                return false;
            }
            previous = chained.eventHash();
        }
        return true;
    }

    public int size() {
        return events.size();
    }
}
