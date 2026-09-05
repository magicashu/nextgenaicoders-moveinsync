-- metrics-v1.1 / M11 low driver-rating rate: driver_rating in {1,2} / rows with driver_rating > 0, valid riders only. Grain: feedback.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE driver_rating IN (1, 2)) AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE driver_rating IN (1, 2)) / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM feedback
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND is_valid_rider AND driver_rating > 0
GROUP BY 1;
