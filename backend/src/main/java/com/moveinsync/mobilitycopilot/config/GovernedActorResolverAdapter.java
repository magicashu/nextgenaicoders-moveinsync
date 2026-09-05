package com.moveinsync.mobilitycopilot.config;

import com.moveinsync.mobilitycopilot.access.application.IdentityResolver;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TrustedIdentity;
import com.moveinsync.mobilitycopilot.api.security.ActorResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Adapts trusted gateway headers to the fail-closed identity and tenant registry. */
@Component
@ConditionalOnProperty(name = "mobility.api.actor-resolver", havingValue = "governance", matchIfMissing = true)
public final class GovernedActorResolverAdapter implements ActorResolver {

    private final IdentityResolver identities;

    public GovernedActorResolverAdapter(IdentityResolver identities) {
        this.identities = identities;
    }

    @Override
    public ActorContext resolve(String subject, String businessUnit, Set<String> roles) {
        return identities.resolve(new TrustedIdentity(subject, businessUnit, roles, "trusted-api-gateway"));
    }
}
