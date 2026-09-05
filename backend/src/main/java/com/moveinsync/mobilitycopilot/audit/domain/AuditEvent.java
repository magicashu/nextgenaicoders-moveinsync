package com.moveinsync.mobilitycopilot.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        UUID runId,
        String businessUnit,
        String eventType,
        Map<String, String> payload,
        Instant occurredAt,
        String traceId) {

    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(businessUnit, "businessUnit is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(traceId, "traceId is required");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
