package com.moveinsync.mobilitycopilot.metrics.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import java.util.Map;

/** WS1: validate dimensions, metric variant, window, scope and data version before querying. */
public record MetricRequest(TenantContext tenant, MetricId metricId, Measure measure,
                            MetricWindow window, Map<String, String> filters, String dataVersion) {
    /** M02 and M12 are metric families; preserve the requested statistic/rating dimension. */
    public enum Measure { VALUE, MEAN_DELAY, P90_DELAY, DRIVER_RATING, SAFETY_RATING }
}
