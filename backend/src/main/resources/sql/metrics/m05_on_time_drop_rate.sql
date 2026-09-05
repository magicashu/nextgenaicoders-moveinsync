-- metrics-v1.1 / M05 on-time drop rate: boarded valid-rider legs dropped within 10 min / boarded legs with both drop epochs. Grain: leg.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE drop_deviation_minutes <= 10) AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE drop_deviation_minutes <= 10) / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM legs
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND is_valid_rider AND is_boarded AND drop_deviation_minutes IS NOT NULL
GROUP BY 1;
