-- Cross-tenant peer comparison of M01 for one window. Exposed only to the facilities-head persona by the caller.
SELECT business_unit AS member,
       count(*) FILTER (WHERE is_delayed) AS numerator,
       count(*) AS denominator,
       100.0 * count(*) FILTER (WHERE is_delayed) / nullif(count(*), 0) AS value
FROM trips
WHERE trip_date BETWEEN {{start}} AND {{end}}
GROUP BY 1
ORDER BY 4 DESC;
