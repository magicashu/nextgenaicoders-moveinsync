package com.moveinsync.mobilitycopilot.action.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.access.domain.TripKey;
import java.util.Map;
import java.util.Set;

/** WS3: validate tenant-qualified trips and allowlisted explicit dimensions. */
public record ActionTarget(TenantContext tenant, Set<TripKey> trips, Map<String, String> dimensions) {}
