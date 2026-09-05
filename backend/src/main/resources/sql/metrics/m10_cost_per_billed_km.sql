-- metrics-v1.1 / M10 cost per billed km: sum cost / sum billed km over lines with km > 0 and cost > 0. Grain: bill.
SELECT
    {{dimension}} AS member,
    sum(billed_cost) AS numerator,
    sum(billed_km) AS denominator,
    sum(billed_cost) / nullif(sum(billed_km), 0) AS value,
    count(*) AS supporting_count
FROM bills
WHERE business_unit = {{bu}} AND event_date BETWEEN {{start}} AND {{end}} {{filters}}
  AND trip_id IS NOT NULL AND NOT is_adjustment AND billed_cost > 0 AND billed_km > 0
GROUP BY 1;
