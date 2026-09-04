package com.moveinsync.mobilitycopilot.api.security;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Registry-backed resolver mirroring the governance workstream's identity rules (five official
 * business units, named roles). The composition root replaces it with the governance
 * IdentityResolver adapter by setting {@code mobility.api.actor-resolver=governance}.
 */
@Component
@ConditionalOnProperty(name = "mobility.api.actor-resolver", havingValue = "allowlist", matchIfMissing = true)
public class AllowlistActorResolver implements ActorResolver {

    static final Set<String> TENANTS = Set.of("pinnacle-Slc", "vanta-Sea", "vanta-Aus", "catalyst-Sac", "orbit-Slc");
    static final Set<String> ROLES = Set.of("TRANSPORT_MANAGER", "FACILITIES_HEAD", "LINE_MANAGER", "SCHEDULER", "AUDITOR");

    @Override
    public ActorContext resolve(String subject, String businessUnit, Set<String> roles) {
        if (subject == null || subject.isBlank()) {
            throw new SecurityException("Missing trusted actor identity");
        }
        if (businessUnit == null || !TENANTS.contains(businessUnit.trim())) {
            throw new SecurityException("Business unit is not registered for this deployment");
        }
        Set<String> accepted = new LinkedHashSet<>();
        for (String role : roles) {
            String normalised = role.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (ROLES.contains(normalised)) {
                accepted.add(normalised);
            }
        }
        if (accepted.isEmpty()) {
            throw new SecurityException("No recognised role in trusted identity");
        }
        return new ActorContext(subject.trim(), businessUnit.trim(), accepted);
    }
}
