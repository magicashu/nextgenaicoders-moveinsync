package com.moveinsync.mobilitycopilot.metrics.domain;

import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The eighteen governed metric contracts, metrics-v1.1 (D-031/D-039). */
public final class MetricRegistry {

    public static final String CONTRACT_VERSION = "metrics-v1.1";
    public static final int DEFAULT_MINIMUM_VOLUME = 300;
    public static final int VENDOR_MINIMUM_VOLUME = 500;

    private static final Set<Dimension> ALL = Set.of(Dimension.values());
    private static final Set<Dimension> TRIP_DIMS = ALL;
    private static final Map<MetricId, MetricDefinition> DEFINITIONS = new EnumMap<>(MetricId.class);

    static {
        register(new MetricDefinition(MetricId.M01_DELAYED_TRIP_RATE, "Delayed-trip rate", MetricUnit.PERCENT, MetricGrain.TRIP,
                "trips with delay_minutes > 0", "trips", TRIP_DIMS, Set.of(), Set.of(DatasetFile.RIDES),
                "sql/metrics/m01_delayed_trip_rate.sql", DEFAULT_MINIMUM_VOLUME, List.of(), true, true));
        register(new MetricDefinition(MetricId.M02_DELAY_MINUTES, "Delay of delayed trips (mean or P90)", MetricUnit.MINUTES, MetricGrain.TRIP,
                "delay_minutes capped at 600", "delayed trips", TRIP_DIMS, Set.of("statistic"), Set.of(DatasetFile.RIDES),
                "sql/metrics/m02_delay_minutes_mean.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("delay = 0", "delay > 1,440 min quarantined"), true, false));
        register(new MetricDefinition(MetricId.M03_DELAY_REASON_MIX, "Delay-reason share", MetricUnit.PERCENT, MetricGrain.TRIP,
                "delayed trips with the selected reason", "delayed trips", TRIP_DIMS, Set.of("delay_reason"), Set.of(DatasetFile.RIDES),
                "sql/metrics/m03_delay_reason_share.sql", DEFAULT_MINIMUM_VOLUME, List.of(), false, true));
        register(new MetricDefinition(MetricId.M04_ON_TIME_PICKUP_RATE, "On-time pickup rate", MetricUnit.PERCENT, MetricGrain.LEG,
                "boarded legs picked up within 10 min", "boarded legs with both pickup epochs", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.LEGS), "sql/metrics/m04_on_time_pickup_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("either pickup epoch null", "boarding_status != Boarded", "stwid = 0"), false, true));
        register(new MetricDefinition(MetricId.M05_ON_TIME_DROP_RATE, "On-time drop rate", MetricUnit.PERCENT, MetricGrain.LEG,
                "boarded legs dropped within 10 min", "boarded legs with both drop epochs", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.LEGS), "sql/metrics/m05_on_time_drop_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("either drop epoch null", "boarding_status != Boarded", "stwid = 0"), false, true));
        register(new MetricDefinition(MetricId.M06_NO_SHOW_RATE, "No-show rate", MetricUnit.PERCENT, MetricGrain.LEG,
                "valid employee legs with is_no_show", "all valid employee legs", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.LEGS), "sql/metrics/m06_no_show_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("stwid = 0", "exact duplicate legs"), true, true));
        register(new MetricDefinition(MetricId.M07_DASHBOARD_CANCELLATION_RATE, "Dashboard-cancellation rate", MetricUnit.PERCENT, MetricGrain.LEG,
                "legs cancelled from dashboard", "planned valid legs", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.LEGS), "sql/metrics/m07_dashboard_cancellation_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("stwid = 0", "exact duplicate legs"), true, true));
        register(new MetricDefinition(MetricId.M08_OCCUPANCY_RATE, "Occupancy", MetricUnit.PERCENT, MetricGrain.TRIP,
                "min(actual riders, capacity)", "capacity", TRIP_DIMS, Set.of(), Set.of(DatasetFile.RIDES),
                "sql/metrics/m08_occupancy_rate.sql", DEFAULT_MINIMUM_VOLUME, List.of("capacity null"), false, true));
        register(new MetricDefinition(MetricId.M09_MEDIAN_COST_PER_TRIP, "Median billed cost per trip", MetricUnit.CURRENCY, MetricGrain.BILL,
                "median of per-trip positive billed cost", "billed trips", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.BILLS), "sql/metrics/m09_median_cost_per_trip.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("trip_cost < 0 (adjustments)", "null trip_id", "exact duplicates"), true, false));
        register(new MetricDefinition(MetricId.M10_COST_PER_BILLED_KM, "Cost per billed km", MetricUnit.CURRENCY_PER_KM, MetricGrain.BILL,
                "sum trip_cost", "sum total_trip_km", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.BILLS), "sql/metrics/m10_cost_per_billed_km.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("total_trip_km = 0", "negatives"), true, false));
        register(new MetricDefinition(MetricId.M11_LOW_DRIVER_RATING_RATE, "Low driver-rating rate", MetricUnit.PERCENT, MetricGrain.FEEDBACK,
                "driver_rating in {1,2}", "feedback rows with driver_rating > 0", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.FEEDBACK), "sql/metrics/m11_low_driver_rating_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("driver rating 0", "stwid = 0", "exact duplicates"), true, true));
        register(new MetricDefinition(MetricId.M12_MEAN_DRIVER_SAFETY_RATING, "Mean driver or safety rating", MetricUnit.RATING, MetricGrain.FEEDBACK,
                "sum of ratings > 0", "ratings > 0", ALL, Set.of("rating"),
                Set.of(DatasetFile.RIDES, DatasetFile.FEEDBACK), "sql/metrics/m12_mean_rating_driver.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("rating 0"), false, false));
        register(new MetricDefinition(MetricId.M13_ALERT_RATE, "Alert rate", MetricUnit.PER_1000_TRIPS, MetricGrain.TRIP,
                "alerts excluding sign-off violations", "trips x 1,000", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.ALERTS), "sql/metrics/m13_alert_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("EMPLOYEE_SIGN_OFF_TIME_VIOLATION"), true, true));
        register(new MetricDefinition(MetricId.M14_SEVERE_ALERT_RATE, "Sev-1/2 alert rate", MetricUnit.PER_1000_TRIPS, MetricGrain.TRIP,
                "alerts with severity Sev-1 or Sev-2", "trips x 1,000", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.ALERTS), "sql/metrics/m14_severe_alert_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("EMPLOYEE_SIGN_OFF_TIME_VIOLATION"), true, true));
        register(new MetricDefinition(MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90, "Sev-1/2 acknowledgement P90", MetricUnit.MINUTES, MetricGrain.ALERT,
                "P90 of acknowledge_time - start_time", "Sev-1/2 alerts with valid acknowledgement", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.ALERTS), "sql/metrics/m15_severe_alert_acknowledgement_p90.sql", 20,
                List.of("other/null severity", "null or negative acknowledgement", "sign-off violations"), true, false));
        register(new MetricDefinition(MetricId.M16_TRACKING_GAP_RATE, "Tracking-gap rate", MetricUnit.PER_1000_TRIPS, MetricGrain.TRIP,
                "DEVICE_NOT_REACHABLE alerts", "trips x 1,000", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.ALERTS), "sql/metrics/m16_tracking_gap_rate.sql", DEFAULT_MINIMUM_VOLUME,
                List.of("tenants with zero events are unsupported"), true, true));
        register(new MetricDefinition(MetricId.M17_EV_SHARE, "EV share", MetricUnit.PERCENT, MetricGrain.TRIP,
                "trips with Electric fuel", "trips", TRIP_DIMS, Set.of(), Set.of(DatasetFile.RIDES),
                "sql/metrics/m17_ev_share.sql", DEFAULT_MINIMUM_VOLUME, List.of(), false, true));
        register(new MetricDefinition(MetricId.M18_ESCORT_PRESENT_RATE, "Escort-present rate", MetricUnit.PERCENT, MetricGrain.TRIP,
                "distinct trips with actual_escort among WOMAN_TRAVELLING_ALONE trips", "distinct trips with a WOMAN_TRAVELLING_ALONE alert", ALL, Set.of(),
                Set.of(DatasetFile.RIDES, DatasetFile.ALERTS), "sql/metrics/m18_escort_present_rate.sql", 20,
                List.of("tenants without the alert type are unsupported", "no compliance claim without an external rule"), false, true));
    }

    private MetricRegistry() {
    }

    private static void register(MetricDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static MetricDefinition definition(MetricId id) {
        MetricDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new MetricRequestException("UNKNOWN_METRIC", "No governed contract for " + id);
        }
        return definition;
    }

    public static List<MetricDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    /** Resolves the reviewed SQL resource, honouring variant selectors for M02 and M12. */
    public static String sqlResource(MetricId id, Map<String, String> filters) {
        return switch (id) {
            case M02_DELAY_MINUTES -> "P90".equalsIgnoreCase(filters.getOrDefault("statistic", "MEAN"))
                    ? "sql/metrics/m02_delay_minutes_p90.sql" : "sql/metrics/m02_delay_minutes_mean.sql";
            case M12_MEAN_DRIVER_SAFETY_RATING -> "safety".equalsIgnoreCase(filters.getOrDefault("rating", "driver"))
                    ? "sql/metrics/m12_mean_rating_safety.sql" : "sql/metrics/m12_mean_rating_driver.sql";
            default -> definition(id).sqlResource();
        };
    }

    public static int minimumVolume(MetricId id, Dimension dimension) {
        if (dimension == Dimension.VENDOR_ID) {
            return VENDOR_MINIMUM_VOLUME;
        }
        return definition(id).minimumVolume();
    }
}
