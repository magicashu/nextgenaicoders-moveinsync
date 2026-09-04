package com.moveinsync.mobilitycopilot.access.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Allowlist of business units the deployment serves. Tenant identifiers from any request must be
 * resolved against it; an unknown tenant fails closed before any analytical call.
 */
public final class TenantRegistry {

    public static final Set<String> OFFICIAL_BUSINESS_UNITS = Set.of("pinnacle-Slc", "vanta-Sea", "vanta-Aus", "catalyst-Sac", "orbit-Slc");

    private final Set<String> businessUnits;

    public TenantRegistry(Set<String> businessUnits) {
        this.businessUnits = Set.copyOf(businessUnits);
    }

    public static TenantRegistry official() {
        return new TenantRegistry(OFFICIAL_BUSINESS_UNITS);
    }

    public Optional<TenantContext> resolve(String businessUnit) {
        if (businessUnit == null || businessUnit.isBlank()) {
            return Optional.empty();
        }
        String trimmed = businessUnit.trim();
        return businessUnits.contains(trimmed) ? Optional.of(new TenantContext(trimmed)) : Optional.empty();
    }

    public Set<String> businessUnits() {
        return businessUnits;
    }
}
