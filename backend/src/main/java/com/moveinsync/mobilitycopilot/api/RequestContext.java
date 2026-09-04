package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.api.security.ActorResolver;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Builds the actor and tenant for every request from trusted headers only. */
@Component
public class RequestContext {

    private final ActorResolver resolver;

    public RequestContext(ActorResolver resolver) {
        this.resolver = resolver;
    }

    public ActorContext actor(String actorHeader, String businessUnitHeader, String rolesHeader) {
        Set<String> roles = new LinkedHashSet<>(Arrays.asList((rolesHeader == null || rolesHeader.isBlank() ? TrustedHeaders.DEFAULT_ROLES : rolesHeader).split(",")));
        return resolver.resolve(actorHeader == null || actorHeader.isBlank() ? TrustedHeaders.DEFAULT_ACTOR : actorHeader, businessUnitHeader, roles);
    }

    public TenantContext tenant(ActorContext actor) {
        return new TenantContext(actor.businessUnit());
    }

    public static String persona(ActorContext actor, String requested) {
        if (requested != null && !requested.isBlank()) {
            String upper = requested.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
            if (actor.roles().contains(upper)) {
                return upper;
            }
            throw new SecurityException("Requested persona is not among the actor's roles");
        }
        return actor.roles().contains("FACILITIES_HEAD") ? "FACILITIES_HEAD" : actor.roles().contains("TRANSPORT_MANAGER") ? "TRANSPORT_MANAGER" : actor.roles().iterator().next();
    }
}
