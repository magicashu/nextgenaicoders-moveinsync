-- metrics-v1.1 / M13 alert rate: alerts excluding EMPLOYEE_SIGN_OFF_TIME_VIOLATION / trips x 1,000. Grain: trip.
WITH a AS (
    SELECT {{dimension}} AS member, count(*) AS alerts
    FROM alerts
    WHERE business_unit = {{bu}} AND event_date BETWEEN {{start}} AND {{end}} {{filters}} AND NOT is_excluded_event_type
    GROUP BY 1
),
t AS (
    SELECT {{dimension}} AS member, count(*) AS trips
    FROM trips
    WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
    GROUP BY 1
)
SELECT
    t.member,
    COALESCE(a.alerts, 0) AS numerator,
    t.trips AS denominator,
    1000.0 * COALESCE(a.alerts, 0) / nullif(t.trips, 0) AS value,
    t.trips AS supporting_count
FROM t
LEFT JOIN a ON a.member IS NOT DISTINCT FROM t.member;
