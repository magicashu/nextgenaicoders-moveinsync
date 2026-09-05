-- Weekly alert volume per event type for one tenant across the loaded range (regime-change detection input).
SELECT event_type, week_start, alerts
FROM snapshot_weekly_alert_types
WHERE business_unit = {{bu}}
ORDER BY event_type, week_start;
