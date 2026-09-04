package com.moveinsync.mobilitycopilot.ingestion.domain;

import java.util.List;

/**
 * The seven immutable organizer files. Column lists are the canonical source
 * schema (D-029/D-030); a missing column is normalised to NULL and reported.
 */
public enum DatasetFile {
    RIDES("trips", "raw_rides", "Ride_data _trip-*.csv", "one trip", true, List.of(
            "business_unit", "office", "product_type", "trip_date", "shift_type", "trip_id", "trip_direction",
            "actual_escort", "vendor_id", "planned_cab_registration", "actual_cab_registration",
            "actual_cab_capacity", "planned_km", "traveled_km", "planned_start_epoch", "planned_end_epoch",
            "actual_start_epoch", "actual_end_epoch", "delay_reason", "delay_minutes", "route_source",
            "actual_cab_fuel_type", "is_driver_nc", "is_cab_nc", "trip_nodal", "plannedemployee_cnt",
            "actualemployee_cnt", "noshow_cnt")),
    LEGS("legs", "raw_legs", "emp_Data.csv", "one employee leg of a trip", false, List.of(
            "business_unit", "office", "product_type", "trip_date", "shift_type", "trip_id",
            "planned_pickup_epoch", "planned_drop_epoch", "actual_pickup_epoch", "actual_drop_epoch",
            "planned_km", "traveled_km", "stwid", "signintype", "gender", "emp_role", "boarding_status",
            "not_boarding_reason", "is_no_show")),
    BILLS("bills", "raw_bills", "bill_data.csv", "one billed trip line", false, List.of(
            "business_unit", "office", "vendor", "cycle_start", "cycle_end", "trip_id", "contract",
            "slab_name", "total_trip_km", "trip_cost")),
    FEEDBACK("feedback", "raw_feedback", "trip_feedback.csv", "one rider rating of one leg", false, List.of(
            "business_unit", "trip_id", "trip_type", "trip_date", "stwid", "route_rating", "driver_rating",
            "cab_rating", "safety_rating", "marshal_rating", "creation_time")),
    ALERTS("alerts", "raw_alerts", "alerts_data.csv", "one alert on a trip", false, List.of(
            "business_unit", "trip_id", "stwid", "event_id", "event_type", "start_time", "acknowledge_time",
            "state_text", "severity", "source"));

    private final String tableName;
    private final String rawTableName;
    private final String glob;
    private final String grain;
    private final boolean required;
    private final List<String> columns;

    DatasetFile(String tableName, String rawTableName, String glob, String grain, boolean required, List<String> columns) {
        this.tableName = tableName;
        this.rawTableName = rawTableName;
        this.glob = glob;
        this.grain = grain;
        this.required = required;
        this.columns = columns;
    }

    /** Normalised table name inside DuckDB. */
    public String tableName() {
        return tableName;
    }

    public String rawTableName() {
        return rawTableName;
    }

    public String glob() {
        return glob;
    }

    public String grain() {
        return grain;
    }

    /** Only the ride files are required; every other file degrades a capability. */
    public boolean required() {
        return required;
    }

    public List<String> columns() {
        return columns;
    }
}
