-- Per-tenant structural facts used by the capability matrix.
SELECT
    (SELECT count(*) FROM trips WHERE business_unit = {{bu}}) AS trips,
    (SELECT count(*) FROM (SELECT site_id FROM trips WHERE business_unit = {{bu}} GROUP BY 1 HAVING count(*) >= 300)) AS sites_with_volume,
    (SELECT count(*) FROM (SELECT vendor_id FROM trips WHERE business_unit = {{bu}} GROUP BY 1 HAVING count(*) >= 500)) AS vendors_with_volume,
    (SELECT count(*) FROM alerts WHERE business_unit = {{bu}} AND NOT is_excluded_event_type) AS alerts,
    (SELECT count(*) FROM alerts WHERE business_unit = {{bu}} AND is_severe) AS severe_alerts,
    (SELECT count(*) FROM alerts WHERE business_unit = {{bu}} AND event_type = 'DEVICE_NOT_REACHABLE') AS device_unreachable_alerts,
    (SELECT count(*) FROM alerts WHERE business_unit = {{bu}} AND event_type = 'WOMAN_TRAVELLING_ALONE') AS woman_alone_alerts,
    (SELECT count(*) FROM feedback WHERE business_unit = {{bu}} AND is_valid_rider) AS feedback_rows,
    (SELECT count(*) FROM bills WHERE business_unit = {{bu}}) AS bill_lines,
    (SELECT count(*) FROM legs WHERE business_unit = {{bu}} AND is_valid_rider) AS legs,
    (SELECT count(*) FROM trips WHERE business_unit = {{bu}} AND fuel_type = 'Electric') AS electric_trips;
