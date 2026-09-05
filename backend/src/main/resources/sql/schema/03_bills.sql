-- Normalised bill lines. Source: raw_bills. Exact duplicate lines are dropped (Q3/Q1 handling),
-- remaining multi-line bills are kept and flagged, negative lines are billing adjustments.
CREATE OR REPLACE TABLE bills AS
WITH parsed AS (
    SELECT DISTINCT
        trim(business_unit) AS business_unit,
        office AS site_id,
        vendor AS vendor_id,
        COALESCE(TRY_CAST(cycle_start AS DATE), TRY_CAST(try_strptime(cycle_start, '%B %-d, %Y, %-I:%M %p') AS DATE), TRY_CAST(try_strptime(cycle_start, '%B %-d, %Y') AS DATE)) AS cycle_start,
        COALESCE(TRY_CAST(cycle_end AS DATE), TRY_CAST(try_strptime(cycle_end, '%B %-d, %Y, %-I:%M %p') AS DATE), TRY_CAST(try_strptime(cycle_end, '%B %-d, %Y') AS DATE)) AS cycle_end,
        TRY_CAST(replace(trip_id, ',', '') AS BIGINT) AS trip_id,
        contract,
        slab_name,
        TRY_CAST(replace(total_trip_km, ',', '') AS DOUBLE) AS billed_km,
        TRY_CAST(replace(trip_cost, ',', '') AS DOUBLE) AS billed_cost
    FROM raw_bills
),
counted AS (
    SELECT *, count(*) OVER (PARTITION BY business_unit, trip_id) AS line_count FROM parsed
)
SELECT
    b.* EXCLUDE (line_count),
    b.line_count > 1 AND b.trip_id IS NOT NULL AS multi_line_bill,
    COALESCE(b.billed_cost < 0, FALSE) AS is_adjustment,
    COALESCE(b.billed_km, 0) = 0 AS zero_km,
    COALESCE(t.trip_date, b.cycle_start) AS event_date,
    t.shift_id,
    t.direction,
    t.mode,
    t.fuel_type,
    t.vehicle_id,
    t.trip_id IS NOT NULL AS trip_matched
FROM counted b
LEFT JOIN trips t ON t.business_unit = b.business_unit AND t.trip_id = b.trip_id
WHERE b.business_unit IS NOT NULL;
