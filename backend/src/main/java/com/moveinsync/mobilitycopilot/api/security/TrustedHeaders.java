package com.moveinsync.mobilitycopilot.api.security;

/** Header names carrying the edge-asserted identity (demo stand-in for gateway-injected claims). */
public final class TrustedHeaders {

    public static final String ACTOR = "X-Actor-Id";
    public static final String BUSINESS_UNIT = "X-Business-Unit";
    public static final String ROLES = "X-Roles";
    public static final String DEFAULT_ACTOR = "transport-manager-demo";
    public static final String DEFAULT_ROLES = "TRANSPORT_MANAGER";

    private TrustedHeaders() {
    }
}
