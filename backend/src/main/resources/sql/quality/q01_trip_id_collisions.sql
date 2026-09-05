-- Q1: trip_id values shared by more than one business unit. Joining on trip_id alone mixes tenants.
SELECT count(*) AS affected_rows
FROM (SELECT trip_id FROM trips GROUP BY trip_id HAVING count(DISTINCT business_unit) > 1);
