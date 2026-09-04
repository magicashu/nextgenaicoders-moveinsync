-- Q8: planned headcount differs from actual plus no-show, no-show is derived from legs instead.
SELECT count(*) AS affected_rows FROM trips
WHERE planned_employee_count IS NOT NULL AND actual_employee_count IS NOT NULL AND noshow_count IS NOT NULL
  AND planned_employee_count <> actual_employee_count + noshow_count;
