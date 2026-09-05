package com.moveinsync.mobilitycopilot.metrics.domain;

public enum MetricId {
    M01_DELAYED_TRIP_RATE(MetricUnit.PERCENT),
    M02_DELAYED_TRIP_DELAY(MetricUnit.MINUTES),
    M03_DELAY_REASON_MIX(MetricUnit.PERCENT),
    M04_ON_TIME_PICKUP_RATE(MetricUnit.PERCENT),
    M05_ON_TIME_DROP_RATE(MetricUnit.PERCENT),
    M06_NO_SHOW_RATE(MetricUnit.PERCENT),
    M07_DASHBOARD_CANCELLATION_RATE(MetricUnit.PERCENT),
    M08_OCCUPANCY(MetricUnit.PERCENT),
    M09_MEDIAN_BILLED_COST_PER_TRIP(MetricUnit.CURRENCY),
    M10_COST_PER_BILLED_KM(MetricUnit.CURRENCY_PER_KM),
    M11_LOW_DRIVER_RATING_RATE(MetricUnit.PERCENT),
    M12_MEAN_DRIVER_SAFETY_RATING(MetricUnit.RATING),
    M13_ALERT_RATE(MetricUnit.PER_THOUSAND_TRIPS),
    M14_SEVERE_ALERT_RATE(MetricUnit.PER_THOUSAND_TRIPS),
    M15_SEVERE_ACKNOWLEDGEMENT_P90(MetricUnit.MINUTES),
    M16_TRACKING_GAP_RATE(MetricUnit.PER_THOUSAND_TRIPS),
    M17_EV_SHARE(MetricUnit.PERCENT),
    M18_ESCORT_PRESENT_RATE(MetricUnit.PERCENT);

    private final MetricUnit unit;

    MetricId(MetricUnit unit) { this.unit = unit; }

    public MetricUnit unit() { return unit; }

    public String contractId() { return name().substring(0, 3); }
}
