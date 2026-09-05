package com.moveinsync.mobilitycopilot.api.error;

import java.time.Instant;
import java.util.List;

/** Stable error envelope for validation, authorization, capability and dependency failures. */
public record ApiError(String code, String message, String traceId, Instant occurredAt, List<String> details) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
