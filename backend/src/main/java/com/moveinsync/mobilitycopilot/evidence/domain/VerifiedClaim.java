package com.moveinsync.mobilitycopilot.evidence.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import java.util.Set;

/** WS2: construct only after scope/version/value/citation validation. This record does not verify itself. */
public record VerifiedClaim(String claimId, TenantContext tenant, String dataVersion,
                            String metricVersion, String text, Set<String> evidenceIds,
                            Kind kind) {
    public enum Kind { DIRECT, QUALIFIED_INFERENCE }
}
