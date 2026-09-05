-- Q12: marshal rating 0 means no marshal, excluded from rating averages.
SELECT count(*) AS affected_rows FROM feedback WHERE marshal_rating = 0;
