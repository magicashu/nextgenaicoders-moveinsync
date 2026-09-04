-- Daily M01 / M17 / M08 inputs from the precomputed trip snapshot.
SELECT trip_date, trips, delayed_trips, electric_trips, occupancy_numerator, occupancy_denominator, capped_delay_minutes, capped_delay_trips
FROM snapshot_daily_trips
WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}}
ORDER BY trip_date;
