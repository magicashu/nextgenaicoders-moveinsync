-- Q3: negative bill lines are billing adjustments, excluded from spend metrics.
SELECT count(*) AS affected_rows FROM bills WHERE is_adjustment;
