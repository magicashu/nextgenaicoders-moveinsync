-- Q9: actual headcount above capacity is capped at 100% occupancy.
SELECT count(*) AS affected_rows FROM trips WHERE occupancy_capped;
