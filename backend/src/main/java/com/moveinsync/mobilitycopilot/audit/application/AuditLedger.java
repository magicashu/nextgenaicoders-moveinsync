package com.moveinsync.mobilitycopilot.audit.application;

import com.moveinsync.mobilitycopilot.audit.domain.ChainedAuditEvent;

import java.util.List;
import java.util.UUID;

/** Append-only ledger view. There is deliberately no update or delete operation anywhere in this API. */
public interface AuditLedger extends AuditSink {

    List<ChainedAuditEvent> chainForRun(UUID runId);

    List<ChainedAuditEvent> recentForBusinessUnit(String businessUnit, int limit);

    /** Recomputes the hash chain for a run and returns true when no link is broken. */
    boolean verifyChain(UUID runId);
}
