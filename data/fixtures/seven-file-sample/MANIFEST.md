# Seven-file deterministic fixture (`data/fixtures/seven-file-sample`)

Generated offline by `generate_fixture.py` (seed 20260905) in the exact organizer CSV formats:
comma-formatted ids and epochs in the ride files, ISO dates in `emp_Data.csv`, free-text timestamps in
bills/feedback/alerts, `NA` null tokens, a stray `"False"` severity and drifting boolean casing.
It is synthetic scaffolding for tests, never evidence for a brief.

Tenants: `pinnacle-Slc` (20 trips/day, 2026-05-04..2026-06-07) and `orbit-Slc` (5 trips/day).

| Check | Expected |
|---|---|
| Raw rows rides / legs / bills / feedback / alerts | 875 / 1,401 / 884 / 1,402 / 512 |
| Normalised rows trips / legs / bills | 875 / 1,400 (1 duplicate leg removed) / 877 (7 exact duplicate lines removed) |
| `trip_id` collisions across tenants | 3 (`3,000,001`..`3,000,003`) |
| M01 pinnacle-Slc 2026-06-01..06-07 | 35 / 140 = 25.00%; baseline 2026-05-04..05-31 = 56 / 560 = 10.00% |
| M01 by site (current) | Clearwater Campus 35/70 = 50%, Oakmont Office 0/70 |
| M01 by direction (current) | LOGIN 21/70 = 30%, LOGOUT 14/70 = 20% |
| M02 mean / P90 (current) | 140.80 min / 600.00 min (one 700-minute delay capped) |
| M03 DRIVER share (current) | 12 / 35 = 34.29% |
| M04 on-time pickup (current / baseline) | 185/273 = 67.77% / 994/1092 = 91.03% |
| M06 no-show | 7 / 280 = 2.50% both windows |
| M08 occupancy | 294 / 560 = 52.50% (one trip per day capped at capacity) |
| M09 median cost per trip | 1,040.00 both windows |
| M10 pinnacle / orbit | 83.20 per km / UNSUPPORTED (100% zero km) |
| M11 low driver rating | 7 / 280 = 2.50% |
| M13 alert rate (current / baseline) | 164.29 / 114.29 per 1,000 (sign-off violations excluded) |
| M14 Sev-1/2 rate | 14.29 per 1,000 both windows |
| M15 pinnacle | UNSUPPORTED (10 Sev-1/2 alerts < 20) |
| M16 tracking gap (current / baseline) | 100.00 / 50.00 per 1,000 (doubling) |
| M17 EV share | 20.00% |
| M18 pinnacle / orbit (May) | UNSUPPORTED (no WOMAN_TRAVELLING_ALONE) / 25/25 = 100% |
| Quality findings | 1 negative bill line, 1 null trip_id line, 8 delays > 600 (1 quarantined > 1,440), 1 negative leg distance, 35 occupancy-capped trips, 35 UNCLASSIFIED + 400 null severities, 400 sign-off violations |
| Regime change | `EMPLOYEE_SIGN_OFF_TIME_VIOLATION` 200/week for weeks of 2026-05-04 and 05-11 then 0: STEP_DOWN detected in the week of 2026-05-18 |
| Anomaly detection as-of 2026-06-08 | M01 OPERATIONAL_ANOMALY (HIGH), M04 OPERATIONAL_ANOMALY, M16 OPERATIONAL_ANOMALY (doubling), M06/M11/M13/M14 HEALTHY, orbit-Slc healthy |
