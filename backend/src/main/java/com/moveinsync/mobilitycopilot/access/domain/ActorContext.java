package com.moveinsync.mobilitycopilot.access.domain;

import java.util.Objects;
import java.util.Set;

public record ActorContext(
        String actorId,
        String businessUnit,
        Set<String> roles) {

    public ActorContext {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(businessUnit, "businessUnit is required");
        if (actorId.isBlank() || businessUnit.isBlank()) {
            throw new IllegalArgumentException("actorId and businessUnit must not be blank");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
