package com.moveinsync.mobilitycopilot.access.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Identity asserted by the trusted server edge (gateway, session or mTLS), never by request text.
 * The resolver validates it against the tenant registry and role allowlist before it becomes an actor.
 */
public record TrustedIdentity(String subject, String businessUnit, Set<String> roles, String source) {

    public TrustedIdentity {
        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(businessUnit, "businessUnit is required");
        Objects.requireNonNull(source, "source is required");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
