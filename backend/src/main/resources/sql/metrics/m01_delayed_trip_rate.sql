-- metrics-v1.1 / M01 delayed-trip rate: trips with delay_minutes > 0 / trips. Grain: trip. Exclusions: none.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE is_delayed) AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE is_delayed) / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
GROUP BY 1;
