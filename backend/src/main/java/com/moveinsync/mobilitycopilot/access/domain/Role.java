package com.moveinsync.mobilitycopilot.access.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** Named personas and their deterministic permission sets. Roles are never derived from prose. */
public enum Role {
    TRANSPORT_MANAGER(Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.PROPOSE_ACTION, Permission.APPROVE_ACTION, Permission.VIEW_AUDIT)),
    FACILITIES_HEAD(Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.VIEW_CROSS_TENANT_PEERS, Permission.PROPOSE_ACTION, Permission.APPROVE_ACTION, Permission.VIEW_AUDIT)),
    LINE_MANAGER(Set.of(Permission.READ_TENANT_METRICS, Permission.VIEW_AUDIT)),
    SCHEDULER(Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.PROPOSE_ACTION)),
    AUDITOR(Set.of(Permission.VIEW_AUDIT));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public static Optional<Role> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalised = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Arrays.stream(values()).filter(r -> r.name().equals(normalised)).findFirst();
    }
}
