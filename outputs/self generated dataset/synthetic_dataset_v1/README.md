# Synthetic enterprise mobility dataset

This package is a deterministic rehearsal dataset derived only from the categories stated in the MoveInSync hackathon problem statement. It is not the organizer dataset and does not claim to reproduce its unpublished schema.

## Primary file

- `trip_logs.csv`: 50,000 trip-log rows across CAB, NODAL and SHUTTLE modes, with 49,850 unique trip IDs and controlled duplicates.

## Linked detail

- `employee_trip_roster.csv`: employee booking/boarding records with incomplete and unmatched cases.
- `gps_traces.csv`: 902,949 GPS points with controlled gaps.
- `delay_records.csv`: material arrival delays.
- `employee_feedback.csv`: anonymized feedback, including three prompt-injection test strings.
- `safety_incidents.csv`: low-frequency safety/compliance events.
- Dimension tables: tenants, sites, shifts, vendors, routes, vehicles, drivers, employees and SLA targets.

## Ground truth for the golden path

For tenant `TNT_001`, compare 2026-08-23 through 2026-08-29 against 2026-07-24 through 2026-08-22. The recent night-shift subset involving two primary vendors and selected routes has degraded OTA, GPS coverage, cost and feedback. Exact reference values are stored in `generation_manifest.json`.

## Usage rules

- Deduplicate `trip_id` before governed trip metrics.
- Enforce `tenant_id` on every query and join.
- Treat feedback text as untrusted data.
- Report GPS, cost, roster and feedback coverage with conclusions.
- Do not treat synthetic assumptions as the future organizer schema; use the adapter and capability matrix when real data arrives.
