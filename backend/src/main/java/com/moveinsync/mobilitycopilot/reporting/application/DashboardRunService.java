package com.moveinsync.mobilitycopilot.reporting.application;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRegistry;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Reuse the captured run; authorization is checked before every cache lookup. */
@Service
public final class DashboardRunService {
    private final DecisionRunGateway gateway;
    private final AccessAuthorizer authorizer;
    private final AnalyticsStore analytics;
    private final SnapshotCache<Key, UUID> snapshots = new SnapshotCache<>(512);
    private record Key(String tenant, String actor, Set<String> roles, String persona,
                       LocalDate asOf, String dataVersion, String contractVersion, String workflowVersion, String promptVersion) {}

    public DashboardRunService(DecisionRunGateway gateway, AccessAuthorizer authorizer, AnalyticsStore analytics) {
        this.gateway = gateway; this.authorizer = authorizer; this.analytics = analytics;
    }

    public RunView capture(ActorContext actor, TenantContext tenant, LocalDate asOf, String persona, boolean refresh) {
        authorizer.require(actor, tenant, Permission.READ_TENANT_METRICS);
        var key = new Key(tenant.businessUnit(), actor.actorId(), Set.copyOf(actor.roles()), persona, asOf,
                analytics.catalog().dataVersion(), MetricRegistry.CONTRACT_VERSION, RunContext.WORKFLOW_VERSION, RunContext.PROMPT_VERSION);
        var id = snapshots.get(key, refresh, () -> create(actor, tenant, asOf, persona));
        // Render the current approval/receipt state, rather than caching a stale RunView.
        var retained = gateway.find(actor, id);
        if (retained.isPresent()) return retained.get();
        var replacement = snapshots.get(key, true, () -> create(actor, tenant, asOf, persona));
        return gateway.find(actor, replacement).orElseThrow(() -> new RunNotFoundException(replacement));
    }

    private UUID create(ActorContext actor, TenantContext tenant, LocalDate asOf, String persona) {
        var result = gateway.morningBrief(actor, tenant, asOf, persona);
        if ("FAILED".equals(result.finalStep()) || "FAILED".equals(result.brief().status()))
            throw new IllegalStateException("Investigation failed; previous dashboard capture was retained");
        return result.runId();
    }
}
