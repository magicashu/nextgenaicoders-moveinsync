package com.moveinsync.mobilitycopilot.reporting.adapter;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal implementations of the frozen AccessAuthorizer and AuditSink ports so the API slice boots
 * on its own branch. The governance workstream provides the real ones; the composition root removes
 * these by setting {@code mobility.api.gateway=workflow}.
 */
@Configuration
@ConditionalOnProperty(name = "mobility.api.gateway", havingValue = "scaffold", matchIfMissing = true)
public class ScaffoldPortBeans {

    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            "TRANSPORT_MANAGER", Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.PROPOSE_ACTION, Permission.APPROVE_ACTION, Permission.VIEW_AUDIT),
            "FACILITIES_HEAD", Set.of(Permission.values()),
            "LINE_MANAGER", Set.of(Permission.READ_TENANT_METRICS, Permission.VIEW_AUDIT),
            "SCHEDULER", Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.PROPOSE_ACTION),
            "AUDITOR", Set.of(Permission.VIEW_AUDIT));

    @Bean
    public AccessAuthorizer scaffoldAuthorizer() {
        return (ActorContext actor, TenantContext tenant, Permission permission) -> {
            if (!actor.businessUnit().equals(tenant.businessUnit())) {
                throw new SecurityException("Actor is not authorized for the requested business unit");
            }
            if (actor.roles().stream().noneMatch(r -> ROLE_PERMISSIONS.getOrDefault(r, Set.of()).contains(permission))) {
                throw new SecurityException("Actor lacks " + permission);
            }
        };
    }

    @Bean
    public AuditSink scaffoldAuditSink() {
        List<AuditEvent> events = new CopyOnWriteArrayList<>();
        return new AuditSink() {
            @Override
            public AuditEvent append(AuditEvent event) {
                events.add(event);
                return event;
            }

            @Override
            public List<AuditEvent> findByRunId(UUID runId) {
                return events.stream().filter(e -> e.runId().equals(runId)).toList();
            }
        };
    }
}
