-- metrics-v1.1 / M06 no-show rate: valid employee legs with is_no_show / all valid employee legs (deduplicated). Grain: leg.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE is_no_show) AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE is_no_show) / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM legs
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}} AND is_valid_rider
GROUP BY 1;
