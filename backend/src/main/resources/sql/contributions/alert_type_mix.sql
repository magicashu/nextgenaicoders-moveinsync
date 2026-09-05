-- Alert distribution by event type for one window (includes excluded types so regime changes are visible).
SELECT event_type AS category, count(*) AS count
FROM alerts
WHERE business_unit = {{bu}} AND event_date BETWEEN {{start}} AND {{end}} {{filters}}
GROUP BY 1
ORDER BY 2 DESC;
