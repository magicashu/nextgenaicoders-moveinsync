-- Share of trips with at least one feedback row per tenant.
SELECT t.business_unit,
       count(DISTINCT f.trip_id) * 1.0 / nullif(count(DISTINCT t.trip_id), 0) AS coverage
FROM trips t
LEFT JOIN feedback f ON f.business_unit = t.business_unit AND f.trip_id = t.trip_id
GROUP BY 1;
