-- Q1 fixture 7: rows a trip_id-only join would add for one tenant versus the composite key.
SELECT count(DISTINCT a.trip_id) AS affected_rows
FROM trips a
JOIN trips b ON a.trip_id = b.trip_id AND a.business_unit <> b.business_unit
WHERE a.business_unit = ?;
