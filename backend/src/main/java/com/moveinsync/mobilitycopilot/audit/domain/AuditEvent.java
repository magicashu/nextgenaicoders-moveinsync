package com.moveinsync.mobilitycopilot.audit.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        UUID runId,
        String businessUnit,
        String eventType,
        Instant occurredAt) {
}
