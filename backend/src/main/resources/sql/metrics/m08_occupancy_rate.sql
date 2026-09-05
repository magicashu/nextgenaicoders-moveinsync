-- metrics-v1.1 / M08 occupancy: min(actual riders, capacity) / capacity, trips with capacity only. Grain: trip.
SELECT
    {{dimension}} AS member,
    sum(occupancy_numerator) AS numerator,
    sum(capacity) AS denominator,
    100.0 * sum(occupancy_numerator) / nullif(sum(capacity), 0) AS value,
    count(*) AS supporting_count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}} AND occupancy_numerator IS NOT NULL
GROUP BY 1;
