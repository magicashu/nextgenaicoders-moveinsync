-- metrics-v1.1 / M17 EV share: trips with Electric fuel / trips. Grain: trip.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE fuel_type = 'Electric') AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE fuel_type = 'Electric') / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
GROUP BY 1;
