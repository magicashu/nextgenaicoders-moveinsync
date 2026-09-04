-- Q11: legs without planned epochs cannot enter punctuality denominators.
SELECT count(*) AS affected_rows FROM legs WHERE planned_pickup_epoch IS NULL;
