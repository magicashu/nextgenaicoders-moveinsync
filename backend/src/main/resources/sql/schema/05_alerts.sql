-- Normalised alerts. Source: raw_alerts. Severity 'False' becomes UNCLASSIFIED (Q6), sign-off
-- violations are flagged for exclusion from alert-rate metrics (Q5), acknowledgement minutes are
-- computed only when the acknowledgement follows the start.
CREATE OR REPLACE TABLE alerts AS
WITH parsed AS (
    SELECT
        trim(business_unit) AS business_unit,
        TRY_CAST(replace(trip_id, ',', '') AS BIGINT) AS trip_id,
        TRY_CAST(replace(stwid, ',', '') AS BIGINT) AS rider_id,
        event_id,
        event_type,
        COALESCE(TRY_CAST(start_time AS TIMESTAMP), try_strptime(start_time, '%B %-d, %Y, %-I:%M %p')) AS started_at,
        COALESCE(TRY_CAST(acknowledge_time AS TIMESTAMP), try_strptime(acknowledge_time, '%B %-d, %Y, %-I:%M %p')) AS acknowledged_at,
        state_text AS state,
        CASE
            WHEN severity IN ('Sev-1', 'Sev-2', 'Sev-3') THEN severity
            WHEN severity IS NULL OR trim(severity) = '' OR upper(trim(severity)) IN ('NA', 'NULL') THEN NULL
            ELSE 'UNCLASSIFIED'
        END AS severity,
        nullif(nullif(trim(source), ''), 'NA') AS source
    FROM raw_alerts
)
SELECT
    a.*,
    a.severity IN ('Sev-1', 'Sev-2') AS is_severe,
    a.event_type = 'EMPLOYEE_SIGN_OFF_TIME_VIOLATION' AS is_excluded_event_type,
    CASE WHEN a.acknowledged_at IS NOT NULL AND a.acknowledged_at >= a.started_at
         THEN epoch(a.acknowledged_at - a.started_at) / 60.0 END AS acknowledgement_minutes,
    COALESCE(t.trip_date, CAST(a.started_at AS DATE)) AS event_date,
    t.site_id,
    t.shift_id,
    t.direction,
    t.mode,
    t.vendor_id,
    t.fuel_type,
    t.vehicle_id,
    COALESCE(t.escort_present, FALSE) AS escort_present,
    t.trip_id IS NOT NULL AS trip_matched
FROM parsed a
LEFT JOIN trips t ON t.business_unit = a.business_unit AND t.trip_id = a.trip_id
WHERE a.business_unit IS NOT NULL AND a.started_at IS NOT NULL;
