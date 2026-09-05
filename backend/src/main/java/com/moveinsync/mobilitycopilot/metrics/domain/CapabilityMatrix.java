package com.moveinsync.mobilitycopilot.metrics.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import java.util.Map;

/** WS1: derive from validated data. Dataset support and runtime implementation are separate facts. */
public record CapabilityMatrix(TenantContext tenant, String dataVersion,
                               Map<MetricId, Capability> metrics) {
    public enum Status { SUPPORTED, DERIVABLE_WITH_CAVEAT, UNAVAILABLE }
    public record Capability(Status status, String reason) {}
}
