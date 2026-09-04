package com.moveinsync.mobilitycopilot.access.application;

import com.moveinsync.mobilitycopilot.access.domain.AccessDeniedException;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.Role;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Deterministic authorization: the actor's business unit must equal the requested tenant and one of
 * the actor's roles must grant the permission. There is no override, no prompt and no model.
 */
@Component
public class RoleBasedAccessAuthorizer implements AccessAuthorizer {

    @Override
    public void require(ActorContext actor, TenantContext tenant, Permission permission) {
        if (!actor.businessUnit().equals(tenant.businessUnit())) {
            throw new AccessDeniedException("CROSS_TENANT", "Actor is not authorized for the requested business unit");
        }
        boolean granted = actor.roles().stream().map(Role::parse).flatMap(Optional::stream)
                .anyMatch(role -> role.permissions().contains(permission));
        if (!granted) {
            throw new AccessDeniedException("MISSING_PERMISSION", "Actor lacks " + permission + " for this business unit");
        }
    }

    public boolean allows(ActorContext actor, TenantContext tenant, Permission permission) {
        try {
            require(actor, tenant, permission);
            return true;
        } catch (AccessDeniedException denied) {
            return false;
        }
    }
}
