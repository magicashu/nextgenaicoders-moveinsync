-- Normalised trip table (one row per (business_unit, trip_id)). Source: raw_rides.
-- Rules (D-029/D-031): strip thousands separators, parse free-text or ISO dates, keep the organizer
-- delayed flag, cap delay at 600 min for averages and quarantine > 1,440 min, cap occupancy at 100%.
CREATE OR REPLACE TABLE trips AS
WITH parsed AS (
    SELECT
        trim(business_unit) AS business_unit,
        TRY_CAST(replace(trip_id, ',', '') AS BIGINT) AS trip_id,
        office AS site_id,
        shift_type AS shift_id,
        trip_direction AS direction,
        product_type AS mode,
        vendor_id,
        actual_cab_registration AS vehicle_id,
        TRY_CAST(actual_cab_capacity AS INTEGER) AS capacity,
        actual_cab_fuel_type AS fuel_type,
        COALESCE(TRY_CAST(trip_date AS DATE), TRY_CAST(try_strptime(trip_date, '%B %-d, %Y') AS DATE)) AS trip_date,
        TRY_CAST(replace(planned_km, ',', '') AS DOUBLE) AS planned_km,
        TRY_CAST(replace(traveled_km, ',', '') AS DOUBLE) AS traveled_km,
        TRY_CAST(replace(planned_start_epoch, ',', '') AS BIGINT) AS planned_start_epoch,
        TRY_CAST(replace(planned_end_epoch, ',', '') AS BIGINT) AS planned_end_epoch,
        TRY_CAST(replace(actual_start_epoch, ',', '') AS BIGINT) AS actual_start_epoch,
        TRY_CAST(replace(actual_end_epoch, ',', '') AS BIGINT) AS actual_end_epoch,
        delay_reason,
        TRY_CAST(replace(delay_minutes, ',', '') AS DOUBLE) AS delay_minutes,
        CASE lower(actual_escort) WHEN 'true' THEN TRUE WHEN 'false' THEN FALSE END AS escort_present,
        CASE lower(is_driver_nc) WHEN 'true' THEN TRUE WHEN 'false' THEN FALSE END AS driver_nc,
        CASE lower(is_cab_nc) WHEN 'true' THEN TRUE WHEN 'false' THEN FALSE END AS cab_nc,
        TRY_CAST(plannedemployee_cnt AS INTEGER) AS planned_employee_count,
        TRY_CAST(actualemployee_cnt AS INTEGER) AS actual_employee_count,
        TRY_CAST(noshow_cnt AS INTEGER) AS noshow_count,
        route_source,
        nullif(nullif(trim(trip_nodal), ''), 'NA') AS trip_nodal
    FROM raw_rides
),
keyed AS (
    SELECT *, row_number() OVER (PARTITION BY business_unit, trip_id ORDER BY trip_date, planned_start_epoch) AS rn
    FROM parsed
    WHERE business_unit IS NOT NULL AND trip_id IS NOT NULL AND trip_date IS NOT NULL
)
SELECT
    * EXCLUDE (rn),
    delay_minutes > 0 AS is_delayed,
    CASE WHEN delay_minutes > 1440 THEN NULL ELSE least(delay_minutes, 600) END AS delay_minutes_capped,
    COALESCE(delay_minutes > 1440, FALSE) AS delay_quarantined,
    CASE WHEN capacity IS NULL OR capacity <= 0 THEN NULL ELSE least(actual_employee_count, capacity) END AS occupancy_numerator,
    COALESCE(actual_employee_count > capacity, FALSE) AS occupancy_capped,
    TRY_CAST(shift_id AS TIME) AS shift_start_time
FROM keyed
WHERE rn = 1;
