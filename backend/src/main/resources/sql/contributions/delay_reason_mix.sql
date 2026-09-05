-- M03 distribution: delayed trips by delay reason for one window.
SELECT delay_reason AS category, count(*) AS count
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}} AND is_delayed
GROUP BY 1
ORDER BY 2 DESC;
