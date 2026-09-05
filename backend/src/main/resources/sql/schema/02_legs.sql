-- Normalised employee legs (one row per (business_unit, trip_id, rider_id)). Source: raw_legs.
-- Rules: keep the first duplicate leg by planned pickup, null out negative distances, flag placeholder
-- rider 0, enrich with trip dimensions through the composite key only.
CREATE OR REPLACE TABLE legs AS
WITH parsed AS (
    SELECT
        trim(business_unit) AS business_unit,
        TRY_CAST(replace(trip_id, ',', '') AS BIGINT) AS trip_id,
        TRY_CAST(replace(stwid, ',', '') AS BIGINT) AS rider_id,
        office AS site_id,
        shift_type AS shift_id,
        product_type AS mode,
        COALESCE(TRY_CAST(trip_date AS DATE), TRY_CAST(try_strptime(trip_date, '%B %-d, %Y') AS DATE)) AS trip_date,
        TRY_CAST(TRY_CAST(replace(planned_pickup_epoch, ',', '') AS DOUBLE) AS BIGINT) AS planned_pickup_epoch,
        TRY_CAST(TRY_CAST(replace(planned_drop_epoch, ',', '') AS DOUBLE) AS BIGINT) AS planned_drop_epoch,
        TRY_CAST(TRY_CAST(replace(actual_pickup_epoch, ',', '') AS DOUBLE) AS BIGINT) AS actual_pickup_epoch,
        TRY_CAST(TRY_CAST(replace(actual_drop_epoch, ',', '') AS DOUBLE) AS BIGINT) AS actual_drop_epoch,
        TRY_CAST(replace(planned_km, ',', '') AS DOUBLE) AS planned_km_raw,
        TRY_CAST(replace(traveled_km, ',', '') AS DOUBLE) AS traveled_km_raw,
        signintype AS signin_type,
        gender,
        emp_role,
        boarding_status,
        not_boarding_reason,
        CASE lower(is_no_show) WHEN 'true' THEN TRUE WHEN 'false' THEN FALSE END AS is_no_show
    FROM raw_legs
),
keyed AS (
    SELECT *, row_number() OVER (PARTITION BY business_unit, trip_id, rider_id ORDER BY planned_pickup_epoch NULLS LAST) AS rn
    FROM parsed
    WHERE business_unit IS NOT NULL AND trip_id IS NOT NULL AND rider_id IS NOT NULL AND trip_date IS NOT NULL
)
SELECT
    l.* EXCLUDE (rn, planned_km_raw, traveled_km_raw),
    CASE WHEN l.planned_km_raw < 0 THEN NULL ELSE l.planned_km_raw END AS planned_km,
    CASE WHEN l.traveled_km_raw < 0 THEN NULL ELSE l.traveled_km_raw END AS traveled_km,
    COALESCE(l.planned_km_raw < 0 OR l.traveled_km_raw < 0, FALSE) AS distance_flagged,
    l.rider_id <> 0 AS is_valid_rider,
    l.boarding_status = 'Boarded' AS is_boarded,
    l.not_boarding_reason = 'TRIP_CANCELLED_FROM_DASHBOARD' AS is_dashboard_cancelled,
    CASE WHEN l.actual_pickup_epoch IS NOT NULL AND l.planned_pickup_epoch IS NOT NULL
         THEN (l.actual_pickup_epoch - l.planned_pickup_epoch) / 60.0 END AS pickup_deviation_minutes,
    CASE WHEN l.actual_drop_epoch IS NOT NULL AND l.planned_drop_epoch IS NOT NULL
         THEN (l.actual_drop_epoch - l.planned_drop_epoch) / 60.0 END AS drop_deviation_minutes,
    t.vendor_id,
    t.direction,
    t.fuel_type,
    t.vehicle_id,
    t.trip_id IS NOT NULL AS trip_matched
FROM keyed l
LEFT JOIN trips t ON t.business_unit = l.business_unit AND t.trip_id = l.trip_id
WHERE l.rn = 1;
