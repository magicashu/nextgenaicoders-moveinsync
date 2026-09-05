package com.moveinsync.mobilitycopilot.audit.domain;

/** Audit event with its ledger position and chain hashes, as read back from the ledger. */
public record ChainedAuditEvent(long sequence, AuditEvent event, String previousHash, String eventHash) {
}
