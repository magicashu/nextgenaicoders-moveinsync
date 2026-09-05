package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;

/** WS1: preserve the official per-tenant support and coverage rules. */
public interface CapabilityMatrixService {
    CapabilityMatrix describe(TenantContext tenant, String dataVersion);
}
