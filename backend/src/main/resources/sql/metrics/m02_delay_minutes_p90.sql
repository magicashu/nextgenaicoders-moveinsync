-- metrics-v1.1 / M02 P90 delay of delayed trips, capped at 600 min, > 1,440 quarantined. Grain: trip.
SELECT
    {{dimension}} AS member,
    NULL::DOUBLE AS numerator,
    count(*) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS denominator,
    quantile_cont(delay_minutes_capped, 0.9) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS value,
    count(*) FILTER (WHERE is_delayed AND NOT delay_quarantined) AS supporting_count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
GROUP BY 1;
