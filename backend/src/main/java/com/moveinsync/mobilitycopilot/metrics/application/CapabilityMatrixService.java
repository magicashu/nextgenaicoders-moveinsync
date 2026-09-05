package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;

/** Per-tenant, per-data-version statement of which analyses are supported, derivable or unsupported. */
public interface CapabilityMatrixService {

    CapabilityMatrix matrix(TenantContext tenant);
}
