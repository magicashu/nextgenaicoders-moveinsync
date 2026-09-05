package com.moveinsync.mobilitycopilot.access.domain;

import java.util.Set;

/** WS3: populate from authenticated server identity and implement role/scope validation. */
public record ActorContext(String actorId, Set<String> roles, Set<TenantContext> allowedTenants) {}
