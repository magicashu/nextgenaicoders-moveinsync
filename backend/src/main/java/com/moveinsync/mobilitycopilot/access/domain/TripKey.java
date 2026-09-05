package com.moveinsync.mobilitycopilot.access.domain;

/** WS1: normalize source IDs before construction; WS3: always scope lookup/audit by both fields. */
public record TripKey(TenantContext tenant, long tripId) {}
