-- Q2: share of bill lines with zero billed km per tenant.
SELECT business_unit, count(*) AS lines, count(*) FILTER (WHERE zero_km) AS zero_lines,
       count(*) FILTER (WHERE zero_km) * 1.0 / nullif(count(*), 0) AS zero_share
FROM bills GROUP BY 1;
