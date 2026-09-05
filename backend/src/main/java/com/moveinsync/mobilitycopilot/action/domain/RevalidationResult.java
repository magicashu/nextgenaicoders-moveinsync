package com.moveinsync.mobilitycopilot.action.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RevalidationResult(
        boolean valid,
        String evidenceVersion,
        Instant revalidatedAt,
        List<String> reasons) {

    public RevalidationResult {
        Objects.requireNonNull(evidenceVersion, "evidenceVersion is required");
        Objects.requireNonNull(revalidatedAt, "revalidatedAt is required");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        if (!valid && reasons.isEmpty()) {
            throw new IllegalArgumentException("invalid revalidation requires a reason");
        }
    }
}
