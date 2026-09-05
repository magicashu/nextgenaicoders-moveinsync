-- Daily M04 / M05 / M06 / M07 inputs from the precomputed leg snapshot.
SELECT trip_date, valid_legs, no_show_legs, dashboard_cancelled_legs, pickup_eligible_legs, on_time_pickup_legs, drop_eligible_legs, on_time_drop_legs
FROM snapshot_daily_legs
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}}
ORDER BY trip_date;
