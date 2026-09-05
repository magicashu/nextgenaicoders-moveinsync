-- metrics-v1.1 / M18 escort-present rate: distinct trips with escort among distinct trips with a WOMAN_TRAVELLING_ALONE alert. Descriptive only, no compliance claim.
SELECT
    {{dimension}} AS member,
    count(DISTINCT trip_id) FILTER (WHERE escort_present) AS numerator,
    count(DISTINCT trip_id) AS denominator,
    100.0 * count(DISTINCT trip_id) FILTER (WHERE escort_present) / nullif(count(DISTINCT trip_id), 0) AS value,
    count(DISTINCT trip_id) AS supporting_count
FROM alerts
WHERE business_unit = {{bu}} AND event_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND event_type = 'WOMAN_TRAVELLING_ALONE' AND trip_matched
GROUP BY 1;
