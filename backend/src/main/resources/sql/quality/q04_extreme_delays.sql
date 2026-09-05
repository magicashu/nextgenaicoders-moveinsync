-- Q4: delays above 600 minutes are capped for averages, above 1,440 quarantined.
SELECT count(*) FILTER (WHERE delay_minutes > 600) AS capped_rows,
       count(*) FILTER (WHERE delay_quarantined) AS quarantined_rows
FROM trips;
