-- Rider legs (actual boarded headcount) on delayed trips versus all trips in a window; impact estimation input.
SELECT
    count(*) AS trips,
    count(*) FILTER (WHERE is_delayed) AS delayed_trips,
    COALESCE(sum(actual_employee_count) FILTER (WHERE is_delayed), 0) AS delayed_rider_legs,
    COALESCE(sum(actual_employee_count), 0) AS rider_legs
FROM trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}};
