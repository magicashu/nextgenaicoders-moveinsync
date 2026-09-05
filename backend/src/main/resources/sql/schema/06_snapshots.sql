-- Daily tenant snapshots. Computed once per data version so scheduled runs never rescan raw rows.
CREATE OR REPLACE TABLE snapshot_daily_trips AS
SELECT
    business_unit, trip_date,
    count(*) AS trips,
    count(*) FILTER (WHERE is_delayed) AS delayed_trips,
    sum(delay_minutes_capped) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS capped_delay_minutes,
    count(*) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS capped_delay_trips,
    count(*) FILTER (WHERE fuel_type = 'Electric') AS electric_trips,
    sum(occupancy_numerator) AS occupancy_numerator,
    sum(capacity) FILTER (WHERE occupancy_numerator IS NOT NULL) AS occupancy_denominator,
    sum(actual_employee_count) FILTER (WHERE is_delayed) AS delayed_rider_legs,
    sum(actual_employee_count) AS rider_legs
FROM trips
GROUP BY 1, 2;

CREATE OR REPLACE TABLE snapshot_daily_legs AS
SELECT
    business_unit, trip_date,
    count(*) FILTER (WHERE is_valid_rider) AS valid_legs,
    count(*) FILTER (WHERE is_valid_rider AND is_no_show) AS no_show_legs,
    count(*) FILTER (WHERE is_valid_rider AND is_dashboard_cancelled) AS dashboard_cancelled_legs,
    count(*) FILTER (WHERE is_valid_rider AND is_boarded AND pickup_deviation_minutes IS NOT NULL) AS pickup_eligible_legs,
    count(*) FILTER (WHERE is_valid_rider AND is_boarded AND pickup_deviation_minutes IS NOT NULL AND pickup_deviation_minutes <= 10) AS on_time_pickup_legs,
    count(*) FILTER (WHERE is_valid_rider AND is_boarded AND drop_deviation_minutes IS NOT NULL) AS drop_eligible_legs,
    count(*) FILTER (WHERE is_valid_rider AND is_boarded AND drop_deviation_minutes IS NOT NULL AND drop_deviation_minutes <= 10) AS on_time_drop_legs
FROM legs
GROUP BY 1, 2;

CREATE OR REPLACE TABLE snapshot_weekly_alert_types AS
SELECT
    business_unit,
    event_type,
    date_trunc('week', event_date)::DATE AS week_start,
    count(*) AS alerts
FROM alerts
GROUP BY 1, 2, 3;
