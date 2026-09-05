-- metrics-v1.1 / M03 delay-reason mix: delayed trips with the selected reason / delayed trips. Grain: trip.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE is_delayed AND delay_reason = {{delay_reason}}) AS numerator,
    count(*) FILTER (WHERE is_delayed) AS denominator,
    100.0 * count(*) FILTER (WHERE is_delayed AND delay_reason = {{delay_reason}})
        / nullif(count(*) FILTER (WHERE is_delayed), 0) AS value,
    count(*) FILTER (WHERE is_delayed) AS supporting_count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
GROUP BY 1;
