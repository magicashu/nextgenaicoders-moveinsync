package com.moveinsync.mobilitycopilot.audit.adapter;

import com.moveinsync.mobilitycopilot.audit.adapter.inmemory.InMemoryAuditLedger;
import com.moveinsync.mobilitycopilot.audit.domain.AuditChain;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuditLedgerTest {

    @Test
    void chainsEventsPerRunAndVerifies() {
        InMemoryAuditLedger ledger = new InMemoryAuditLedger();
        UUID run = UUID.randomUUID();
        ledger.append(new AuditEvent(UUID.randomUUID(), run, "pinnacle-Slc", "WORKFLOW_STARTED", Map.of("actor", "scheduler"), Instant.now(), "trace-1"));
        ledger.append(new AuditEvent(UUID.randomUUID(), run, "pinnacle-Slc", "ACTION_AWAITING_APPROVAL", Map.of("actionId", "a1"), Instant.now(), "trace-1"));
        var chain = ledger.chainForRun(run);
        assertThat(chain).hasSize(2);
        assertThat(chain.getFirst().previousHash()).isEqualTo(AuditChain.GENESIS);
        assertThat(chain.get(1).previousHash()).isEqualTo(chain.getFirst().eventHash());
        assertThat(ledger.verifyChain(run)).isTrue();
        assertThat(ledger.recentForBusinessUnit("pinnacle-Slc", 1)).singleElement().extracting(c -> c.event().eventType()).isEqualTo("ACTION_AWAITING_APPROVAL");
        assertThat(ledger.recentForBusinessUnit("orbit-Slc", 5)).isEmpty();
    }

    @Test
    void hashCoversPayloadSoTamperingIsDetectable() {
        AuditEvent original = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "pinnacle-Slc", "APPROVAL_APPROVE", Map.of("decidedBy", "manager-1"), Instant.parse("2026-06-08T08:00:00Z"), "t");
        AuditEvent tampered = new AuditEvent(original.eventId(), original.runId(), original.businessUnit(), original.eventType(), Map.of("decidedBy", "attacker"), original.occurredAt(), "t");
        assertThat(AuditChain.hash(original, AuditChain.GENESIS)).isNotEqualTo(AuditChain.hash(tampered, AuditChain.GENESIS));
        assertThat(AuditChain.hash(original, AuditChain.GENESIS)).isEqualTo(AuditChain.hash(original, AuditChain.GENESIS));
    }
}
