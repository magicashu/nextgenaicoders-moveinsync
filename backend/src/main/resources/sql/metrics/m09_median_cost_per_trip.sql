-- metrics-v1.1 / M09 median billed cost per trip: median of per-trip positive cost after summing retained lines by (business_unit, trip_id). Grain: bill/trip.
-- Exclusions: negative lines (adjustments), null trip_id, exact duplicates (removed at load).
WITH per_trip AS (
    SELECT {{dimension}} AS member, trip_id, sum(billed_cost) AS trip_cost
    FROM bills
    WHERE business_unit = {{bu}} AND event_date BETWEEN {{start}} AND {{end}} {{filters}}
      AND trip_id IS NOT NULL AND NOT is_adjustment
    GROUP BY 1, 2
    HAVING sum(billed_cost) > 0
)
SELECT
    member,
    NULL::DOUBLE AS numerator,
    count(*) AS denominator,
    median(trip_cost) AS value,
    count(*) AS supporting_count
FROM per_trip
GROUP BY 1;
