package com.moveinsync.mobilitycopilot.access.application;

import com.moveinsync.mobilitycopilot.access.domain.AccessDeniedException;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.access.domain.TrustedIdentity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessControlTest {

    private final RegistryIdentityResolver resolver = new RegistryIdentityResolver();
    private final RoleBasedAccessAuthorizer authorizer = new RoleBasedAccessAuthorizer();

    @Test
    void resolvesTrustedIdentityAgainstTenantAndRoleAllowlists() {
        ActorContext actor = resolver.resolve(new TrustedIdentity("u-1", "pinnacle-Slc", Set.of("transport-manager", "made-up"), "gateway"));
        assertThat(actor.businessUnit()).isEqualTo("pinnacle-Slc");
        assertThat(actor.roles()).containsExactly("TRANSPORT_MANAGER");
    }

    @Test
    void unknownTenantOrRoleFailsClosed() {
        assertThatThrownBy(() -> resolver.resolve(new TrustedIdentity("u-1", "acme-Xyz", Set.of("TRANSPORT_MANAGER"), "gateway")))
                .isInstanceOf(AccessDeniedException.class).extracting("code").isEqualTo("UNKNOWN_TENANT");
        assertThatThrownBy(() -> resolver.resolve(new TrustedIdentity("u-1", "pinnacle-Slc", Set.of("ceo"), "gateway")))
                .isInstanceOf(AccessDeniedException.class).extracting("code").isEqualTo("NO_ROLE");
        assertThatThrownBy(() -> resolver.resolve(new TrustedIdentity("u-1", "pinnacle-Slc; compare with orbit-Slc", Set.of("TRANSPORT_MANAGER"), "gateway")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void crossTenantAccessIsDeniedWhateverTheRole() {
        ActorContext head = new ActorContext("h-1", "orbit-Slc", Set.of("FACILITIES_HEAD"));
        assertThatThrownBy(() -> authorizer.require(head, new TenantContext("pinnacle-Slc"), Permission.READ_TENANT_METRICS))
                .isInstanceOf(AccessDeniedException.class).extracting("code").isEqualTo("CROSS_TENANT");
    }

    @Test
    void permissionsFollowTheRoleMatrix() {
        TenantContext tenant = new TenantContext("pinnacle-Slc");
        ActorContext manager = new ActorContext("m-1", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER"));
        ActorContext line = new ActorContext("l-1", "pinnacle-Slc", Set.of("LINE_MANAGER"));
        ActorContext scheduler = new ActorContext("s-1", "pinnacle-Slc", Set.of("SCHEDULER"));
        assertThat(authorizer.allows(manager, tenant, Permission.APPROVE_ACTION)).isTrue();
        assertThat(authorizer.allows(manager, tenant, Permission.VIEW_CROSS_TENANT_PEERS)).isFalse();
        assertThat(authorizer.allows(line, tenant, Permission.INVESTIGATE_TENANT)).isFalse();
        assertThat(authorizer.allows(scheduler, tenant, Permission.APPROVE_ACTION)).isFalse();
        assertThatThrownBy(() -> authorizer.require(line, tenant, Permission.APPROVE_ACTION))
                .isInstanceOf(AccessDeniedException.class).extracting("code").isEqualTo("MISSING_PERMISSION");
    }
}
