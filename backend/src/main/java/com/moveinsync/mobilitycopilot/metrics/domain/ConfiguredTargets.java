package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Default configured targets from the dataset profile section 8. */
public final class ConfiguredTargets {

    public static final String VERSION = "targets-v1";

    private static final Map<MetricId, ConfiguredTarget> DEFAULTS = Map.of(
            MetricId.M01_DELAYED_TRIP_RATE, new ConfiguredTarget(MetricId.M01_DELAYED_TRIP_RATE, new BigDecimal("10.00"), true, ConfiguredTarget.LABEL, VERSION),
            MetricId.M04_ON_TIME_PICKUP_RATE, new ConfiguredTarget(MetricId.M04_ON_TIME_PICKUP_RATE, new BigDecimal("90.00"), false, ConfiguredTarget.LABEL, VERSION),
            MetricId.M06_NO_SHOW_RATE, new ConfiguredTarget(MetricId.M06_NO_SHOW_RATE, new BigDecimal("10.00"), true, ConfiguredTarget.LABEL, VERSION),
            MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90, new ConfiguredTarget(MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90, new BigDecimal("5.00"), true, ConfiguredTarget.LABEL, VERSION));

    private ConfiguredTargets() {
    }

    public static Optional<ConfiguredTarget> defaultFor(MetricId metricId) {
        return Optional.ofNullable(DEFAULTS.get(metricId));
    }
}
