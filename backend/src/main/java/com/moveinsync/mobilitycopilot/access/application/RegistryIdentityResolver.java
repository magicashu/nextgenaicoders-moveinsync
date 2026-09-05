package com.moveinsync.mobilitycopilot.access.application;

import com.moveinsync.mobilitycopilot.access.domain.AccessDeniedException;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Role;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantRegistry;
import com.moveinsync.mobilitycopilot.access.domain.TrustedIdentity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Validates tenant and roles against allowlists; unknown values fail closed. */
@Component
public class RegistryIdentityResolver implements IdentityResolver {

    private final TenantRegistry registry;

    public RegistryIdentityResolver() {
        this(TenantRegistry.official());
    }

    public RegistryIdentityResolver(TenantRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ActorContext resolve(TrustedIdentity identity) {
        if (identity.subject().isBlank()) {
            throw new AccessDeniedException("MISSING_SUBJECT", "Trusted identity has no subject");
        }
        TenantContext tenant = registry.resolve(identity.businessUnit())
                .orElseThrow(() -> new AccessDeniedException("UNKNOWN_TENANT", "Business unit is not registered for this deployment"));
        Set<String> roles = new LinkedHashSet<>();
        for (String raw : identity.roles()) {
            Role.parse(raw).ifPresent(role -> roles.add(role.name()));
        }
        if (roles.isEmpty()) {
            throw new AccessDeniedException("NO_ROLE", "Trusted identity carries no recognised role");
        }
        return new ActorContext(identity.subject(), tenant.businessUnit(), roles);
    }
}
