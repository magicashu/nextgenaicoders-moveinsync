package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.investigation.InvestigationTool;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The seven worker adapters. Each is a thin, typed wrapper over the analytics gateway so the
 * Investigator can only reach allowlisted analyses. Unknown names are rejected before any call.
 */
@Component
public final class WorkerToolRegistry {

    private final Map<WorkerType, InvestigationTool> tools = new EnumMap<>(WorkerType.class);

    public WorkerToolRegistry(AnalyticsGateway gateway) {
        for (WorkerType type : WorkerType.values()) {
            tools.put(type, new GatewayWorkerTool(type, gateway));
        }
    }

    public Optional<InvestigationTool> tool(String workerId) {
        return WorkerType.fromId(workerId).map(tools::get);
    }

    public int size() {
        return tools.size();
    }

    /** Vendor, site/shift/direction, delay reason, cost/billing, feedback, tracking/safety, no-show/roster. */
    static final class GatewayWorkerTool implements InvestigationTool {

        private final WorkerType type;
        private final AnalyticsGateway gateway;

        GatewayWorkerTool(WorkerType type, AnalyticsGateway gateway) {
            this.type = type;
            this.gateway = gateway;
        }

        @Override
        public String name() {
            return type.id();
        }

        @Override
        public WorkerEvidenceDto execute(TenantContext tenant, AnalyticsGateway.WindowDto current, AnalyticsGateway.WindowDto baseline,
                                         Map<String, String> allowlistedFilters) {
            WorkerEvidenceDto evidence = gateway.runWorker(type.id(), tenant, current, baseline, allowlistedFilters);
            if (!evidence.businessUnit().equals(tenant.businessUnit())) {
                throw new IllegalStateException("Tool returned evidence for another business unit");
            }
            return evidence;
        }
    }
}
