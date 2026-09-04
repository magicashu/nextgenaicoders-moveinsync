-- M01-v1: delayed trips / all trips. Tenant and period are mandatory parameters.
SELECT
    count(*) FILTER (WHERE delay_minutes > 0) AS delayed_trips,
    count(*) AS total_trips,
    round(
        100.0 * count(*) FILTER (WHERE delay_minutes > 0) / nullif(count(*), 0),
        2
    ) AS value_percent
FROM trips_normalized
WHERE business_unit = ?
  AND trip_date BETWEEN ? AND ?;
