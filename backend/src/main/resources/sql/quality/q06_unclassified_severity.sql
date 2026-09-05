-- Q6: severity literal 'False' is treated as UNCLASSIFIED, separate from null.
SELECT count(*) FILTER (WHERE severity = 'UNCLASSIFIED') AS unclassified_rows,
       count(*) FILTER (WHERE severity IS NULL) AS null_rows
FROM alerts;
