-- metrics-v1.1 / M02 mean delay of delayed trips, capped at 600 min, > 1,440 quarantined. Grain: trip.
SELECT
    {{dimension}} AS member,
    sum(delay_minutes_capped) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS numerator,
    count(*) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS denominator,
    sum(delay_minutes_capped) FILTER (WHERE is_delayed AND NOT delay_quarantined)
        / nullif(count(*) FILTER (WHERE is_delayed AND NOT delay_quarantined), 0) AS value,
    count(*) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS supporting_count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
GROUP BY 1;
