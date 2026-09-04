-- metrics-v1.1 / M12 mean safety rating over ratings > 0. Grain: feedback.
SELECT
    {{dimension}} AS member,
    sum(safety_rating) AS numerator,
    count(*) AS denominator,
    sum(safety_rating) * 1.0 / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM feedback
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND is_valid_rider AND safety_rating > 0
GROUP BY 1;
