package com.moveinsync.mobilitycopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Operational limits, not business thresholds or performance promises. */
@ConfigurationProperties("mobility.analytics")
public record AnalyticsProperties(@DefaultValue("data/analytics.duckdb") String database,
        @DefaultValue("512MB") String memoryLimit, @DefaultValue("2") int threads,
        @DefaultValue("4") int connections, @DefaultValue("30") int queryTimeoutSeconds,
        @DefaultValue("256") int cacheEntries, @DefaultValue("100") int maxGroups) {
    public AnalyticsProperties {
        if (database == null || database.isBlank() || memoryLimit == null
                || !memoryLimit.matches("[1-9][0-9]*(MB|GB)") || threads < 1 || connections < 1
                || queryTimeoutSeconds < 1 || cacheEntries < 0 || maxGroups < 1 || maxGroups > 1000) {
            throw new IllegalArgumentException("Invalid bounded analytics configuration");
        }
    }
}
