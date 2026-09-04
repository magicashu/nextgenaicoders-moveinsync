-- Join coverage by composite key (business_unit, trip_id).
SELECT 'legs' AS source, count(*) AS rows, count(*) FILTER (WHERE trip_matched) AS matched FROM legs
UNION ALL SELECT 'bills', count(*), count(*) FILTER (WHERE trip_matched) FROM bills
UNION ALL SELECT 'feedback', count(*), count(*) FILTER (WHERE trip_matched) FROM feedback
UNION ALL SELECT 'alerts', count(*), count(*) FILTER (WHERE trip_matched) FROM alerts;
