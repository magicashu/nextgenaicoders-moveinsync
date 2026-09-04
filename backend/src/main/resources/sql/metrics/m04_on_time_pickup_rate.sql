-- metrics-v1.1 / M04 on-time pickup rate: boarded valid-rider legs picked up within 10 min / boarded legs with both pickup epochs. Grain: leg.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE pickup_deviation_minutes <= 10) AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE pickup_deviation_minutes <= 10) / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM legs
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND is_valid_rider AND is_boarded AND pickup_deviation_minutes IS NOT NULL
GROUP BY 1;
