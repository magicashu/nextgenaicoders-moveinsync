-- metrics-v1.1 / M07 dashboard-cancellation rate: legs cancelled from dashboard / planned valid legs. Grain: leg.
SELECT
    {{dimension}} AS member,
    count(*) FILTER (WHERE is_dashboard_cancelled) AS numerator,
    count(*) AS denominator,
    100.0 * count(*) FILTER (WHERE is_dashboard_cancelled) / nullif(count(*), 0) AS value,
    count(*) AS supporting_count
FROM legs
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}} AND is_valid_rider
GROUP BY 1;
