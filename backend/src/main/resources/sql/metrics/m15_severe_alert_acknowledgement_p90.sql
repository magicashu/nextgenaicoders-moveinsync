-- metrics-v1.1 / M15 Sev-1/2 acknowledgement P90 in minutes, excluding sign-off violations and null/negative durations. Grain: alert.
SELECT
    {{dimension}} AS member,
    NULL::DOUBLE AS numerator,
    count(*) AS denominator,
    quantile_cont(acknowledgement_minutes, 0.9) AS value,
    count(*) AS supporting_count
FROM alerts
WHERE business_unit = {{bu}} AND event_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND is_severe AND NOT is_excluded_event_type AND acknowledgement_minutes IS NOT NULL
GROUP BY 1;
