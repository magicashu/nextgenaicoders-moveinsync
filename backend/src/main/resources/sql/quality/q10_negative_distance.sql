-- Q10: negative leg distances are nulled and flagged.
SELECT count(*) AS affected_rows FROM legs WHERE distance_flagged;
