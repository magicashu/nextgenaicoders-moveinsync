-- Normalised rider feedback. Source: raw_feedback. Exact duplicates dropped, marshal 0 means no marshal.
CREATE OR REPLACE TABLE feedback AS
WITH parsed AS (
    SELECT DISTINCT
        trim(business_unit) AS business_unit,
        TRY_CAST(replace(trip_id, ',', '') AS BIGINT) AS trip_id,
        trip_type AS direction,
        COALESCE(TRY_CAST(trip_date AS DATE), TRY_CAST(try_strptime(trip_date, '%B %-d, %Y, %-I:%M %p') AS DATE), TRY_CAST(try_strptime(trip_date, '%B %-d, %Y') AS DATE)) AS trip_date,
        TRY_CAST(replace(stwid, ',', '') AS BIGINT) AS rider_id,
        TRY_CAST(route_rating AS INTEGER) AS route_rating,
        TRY_CAST(driver_rating AS INTEGER) AS driver_rating,
        TRY_CAST(cab_rating AS INTEGER) AS cab_rating,
        TRY_CAST(safety_rating AS INTEGER) AS safety_rating,
        TRY_CAST(marshal_rating AS INTEGER) AS marshal_rating,
        COALESCE(TRY_CAST(creation_time AS TIMESTAMP), try_strptime(creation_time, '%B %-d, %Y, %-I:%M %p')) AS created_at
    FROM raw_feedback
)
SELECT
    f.*,
    f.rider_id <> 0 AS is_valid_rider,
    t.site_id,
    t.shift_id,
    t.mode,
    t.vendor_id,
    t.fuel_type,
    t.vehicle_id,
    t.trip_id IS NOT NULL AS trip_matched
FROM parsed f
LEFT JOIN trips t ON t.business_unit = f.business_unit AND t.trip_id = f.trip_id
WHERE f.business_unit IS NOT NULL AND f.trip_id IS NOT NULL AND f.trip_date IS NOT NULL;
