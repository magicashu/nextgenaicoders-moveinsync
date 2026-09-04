# Official Dataset Profile, Field Map, Capability Matrix and Metric Contracts

Date: 2026-09-04
Status: Accepted evidence for D-029 through D-033
Source: `outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset/` (seven CSVs plus `Dictionary/`)
Profiling method: offline pandas scripts under `tmp/profile/` (allowed by D-028 for offline dataset validation). Raw outputs: `tmp/profile/rides_profile.txt`, `tmp/profile/other_profile.txt`, `tmp/profile/anomaly_profile.txt`. Checksums: `tmp/profile/checksums.sha256`.

This document replaces every synthetic-dataset assumption in D-022 and D-023. Numbers here were computed directly from the organizer files and must be reproduced by the DuckDB metric layer before they appear in any brief.

## 1. Inventory and checksums

| File | Rows (data) | Size | SHA-256 (first 16) | Grain |
|---|---:|---:|---|---|
| `Ride_data _trip-may_2026.csv` | 188,992 | 45.9 MB | `c449ec4a4f35c84d` | one trip |
| `Ride_data _trip-June_2026.csv` | 210,669 | 51.5 MB | `01839a6cff0c86ef` | one trip |
| `Ride_data _trip-July_2026.csv` | 215,885 | 52.9 MB | `76da3741db9f0576` | one trip |
| `emp_Data.csv` | 1,637,906 | 286.0 MB | `147af45449d1f154` | one employee leg of a trip |
| `bill_data.csv` | 620,942 | 86.5 MB | `abe6e0be97880d08` | one billed trip line |
| `trip_feedback.csv` | 512,873 | 51.1 MB | `662254358115429c` | one rider rating of one leg |
| `alerts_data.csv` | 51,699 | 7.9 MB | `34b8fa3885c4db72` | one alert on a trip |

Total: 615,546 trips, 1,637,906 employee legs, 25,191 distinct real riders, 23 vendors, 19 offices, 4,171 vehicle plates, 5 business units, 2026-05-01 through 2026-07-31. Keep the original files unchanged.

Full checksums:

```text
34b8fa3885c4db729749f956d26c3ba5603e565e872544ef018eca4ff4c86007 alerts_data.csv
abe6e0be97880d08ff08738091c7048a707259515432785c8bbe6b19baee82a3 bill_data.csv
147af45449d1f154871c14fa90b92037a6d7d887d7cee2a892963123dd63232d emp_Data.csv
76da3741db9f0576671d8b9cea893a85a7504ec94cdda415f5e69ecf6d00ad13 Ride_data _trip-July_2026.csv
01839a6cff0c86ef09c467418a3516ca3006ccff6cc9b51c6fb1f35ff502c744 Ride_data _trip-June_2026.csv
c449ec4a4f35c84d46f922435feef78876c273e7ff5257dd760b226374a2e3da Ride_data _trip-may_2026.csv
662254358115429c14b912c0925813e2c0d243f7a83369d1856ad5229109405c trip_feedback.csv
```

## 2. What the dataset does not contain

These absences change the frozen plan and are recorded in D-030.

| Assumed in earlier plan | Reality | Replacement |
|---|---|---|
| GPS traces (lat/lon pings) | None. No coordinates anywhere. | Tracking coverage is proxied by `DEVICE_NOT_REACHABLE` alerts and by null `actual_*_epoch` values. No location conclusions are possible. |
| Driver ID | None. Only `is_driver_nc` (trip) and `driver_rating` (feedback). | Driver quality is analysed at vendor grain plus vehicle plate. |
| Route ID | None. | Route proxy = `office` × `shift_type` × `trip_direction`. |
| SLA / goal targets | None. | Configured per-tenant thresholds, labelled "configured target", plus historical and peer baselines. |
| Budget / quoted cost | None. Only billed `trip_cost`. | Cost trend and peer comparison only; no budget variance. |
| Roster file | `emp_Data.csv` is the roster equivalent (one row per planned rider per trip). | Rider-level denominators are supported. |
| Text feedback | None. Ratings 0-5 only. | No free-text mining; no prompt-injection surface in feedback. |
| Globally unique `trip_id` | 6,753 IDs collide across business units. | Composite key `(business_unit, trip_id)` everywhere. |

## 3. Tenant and key model

- **Tenant = `business_unit`.** Five tenants: `pinnacle-Slc` (251,774 trips), `vanta-Sea` (180,064), `vanta-Aus` (70,199), `catalyst-Sac` (65,214), `orbit-Slc` (48,295). Vendors and offices are shared across tenants, so every metric, cache, evidence ID and action must carry the tenant.
- **Trip key = `(business_unit, trip_id)`.** `trip_id` ranges overlap between `orbit-Slc` (1,208,678-1,368,372) and `vanta-Aus` (1,097,349-1,260,162). Within a tenant `trip_id` is unique in the ride files (0 duplicates).
- **Leg key = `(business_unit, trip_id, stwid)`.** 708 duplicate legs exist; keep the first by planned pickup and report the count.
- **Rider key = `stwid`**, with `0` treated as "no rider" (1,414 leg rows, most alerts).
- **Bill key** = `(business_unit, trip_id)` with 157 duplicate lines, 72 of them exact duplicates. Exact duplicates are dropped; the remaining 85 are kept and flagged as multi-line bills.
- **Normalisation rules per file:** strip thousands separators from `trip_id`, `stwid`, `*_epoch`, `delay_minutes`, `trip_cost`; parse `trip_date` per file format; cast `is_driver_nc`/`is_cab_nc` from `true/false/True/False/null` to nullable boolean; parse `planned_km` as float after stripping one comma-formatted value in July.
- **Time zone:** epochs interpreted as UTC agree with `trip_date` on 99.98% of trips and with `shift_type` hours. Treat epochs as already-local wall-clock and do no conversion.

## 4. Join coverage

| Join | Coverage | Notes |
|---|---:|---|
| emp leg → trip | 100% both ways | Every trip has at least one leg |
| bill → trip | 99.0% of bill lines match; 99.9% of trips billed | 160 bill lines have null `trip_id` |
| feedback → trip | 99.97% of feedback rows match | Only 49.5% of trips have any feedback |
| alerts → trip | 99.1% of alerts match | Only 5.5% of trips have an alert |

Feedback coverage by tenant: `orbit-Slc` 95.6%, `pinnacle-Slc` 93.5%, `catalyst-Sac` 11.6%, `vanta-Aus` 3.9%, `vanta-Sea` 3.7%. Feedback conclusions for the last three tenants must be marked low-coverage.

## 5. Data-quality findings beyond the organizer README

| # | Finding | Scale | Handling rule |
|---:|---|---:|---|
| Q1 | `trip_id` collides across tenants | 6,753 IDs | Composite key; never join on `trip_id` alone |
| Q2 | Billed `total_trip_km` is zero | 100% of `vanta-Aus`, 96.7% of `vanta-Sea`, 40% overall | Cost-per-km unsupported for those tenants; cost-per-trip still supported |
| Q3 | Negative `trip_cost` | 189 lines; `Meera Lebedev Travel` -14.66M across 152 lines in `vanta-Sea` May | Exclude negatives from spend metrics, report them as billing adjustments; use medians for peer comparison |
| Q4 | Extreme `delay_minutes` | 136 trips > 600 min, max 10,644 min (7.4 days) | Cap at 600 min for averages, quarantine > 1,440 min, always keep the late flag |
| Q5 | `EMPLOYEE_SIGN_OFF_TIME_VIOLATION` alerts | 7,670 in May (weeks of May 4-17 only), 46 in June, 20 in July, all `pinnacle-Slc` | Alert-configuration change, not an operations signal. Golden false-anomaly case: detector must classify as data-regime change and not escalate |
| Q6 | `severity = "False"` | 15,037 rows (29%) | Treat as "unclassified", separate from null (16,348). Only `Sev-1/2/3` count toward severity metrics |
| Q7 | Null-severity alerts auto-acknowledged | Median ack 1,444 min (24 h) versus 1-2 min for Sev-1/2 | Exclude null-severity from acknowledgement-time SLA |
| Q8 | `plannedemployee_cnt ≠ actualemployee_cnt + noshow_cnt` | 26% of trips | Derive no-show from `emp_Data`, never from trip counts |
| Q9 | `actualemployee_cnt > actual_cab_capacity` | 1,494 trips | Cap occupancy at 100% and flag |
| Q10 | Negative leg distance | 47 traveled, 1 planned | Null out and flag |
| Q11 | Null planned epochs in legs | 112,943 legs (7%); 14-22% per tenant when including cancelled | On-time pickup metric denominators use boarded legs with both epochs |
| Q12 | `marshal_rating = 0` | 92% of feedback rows | Means "no marshal"; exclude from averages. Other `0` ratings appear on 2 rows only |
| Q13 | Sparse offices | 6 offices with < 400 trips | Suppress below minimum volume (default 300 trips per window) |
| Q14 | `product_type` and `route_source` drift | `SPOT_2.0` = `RENTLZ`; `BUS` = `SHUTTLE_SERVICE`/`MANUAL` | Report mode from `product_type` only |

## 6. Canonical field map

| Canonical field | Source | Notes |
|---|---|---|
| tenant_id | `business_unit` (all files) | |
| trip_id | `trip_id` normalised | composite with tenant |
| site_id | `office` | 19 values |
| shift_id | `shift_type` | `HH:MM`, `Adhoc`, `Non Shift` |
| direction | `trip_direction` (rides), `trip_type` (feedback) | LOGIN = pickup to office, LOGOUT = drop home |
| mode | `product_type` | CAB, BUS, SPOT_2.0 |
| vendor_id | `vendor_id` (rides), `vendor` (bill) | 97.9% agree on joined rows |
| vehicle_id | `actual_cab_registration` | planned plate differs on 0.24% |
| capacity | `actual_cab_capacity` | 3-12 |
| fuel_type | `actual_cab_fuel_type` | Diesel, Petrol, Electric |
| planned/actual trip start/end | `*_start_epoch`, `*_end_epoch` | seconds |
| trip_delay_minutes, delay_reason | `delay_minutes`, `delay_reason` | non-zero on 9.8% of trips, perfectly consistent with reason |
| escort_present | `actual_escort` | 16.5% true |
| driver_nc, cab_nc | `is_driver_nc`, `is_cab_nc` | 784 and 32 true |
| planned/actual employee count, noshow count | `plannedemployee_cnt`, `actualemployee_cnt`, `noshow_cnt` | see Q8 |
| rider_id | `stwid` | 0 = none |
| planned/actual pickup/drop | `planned_pickup_epoch`, … | leg grain |
| boarding_status, no_show, not_boarding_reason, signin_type, gender, role | `emp_Data` columns | |
| billed_cost, billed_km, contract, slab, cycle | `bill_data` columns | semi-monthly cycles for two tenants, monthly otherwise |
| route/driver/cab/safety/marshal rating | `trip_feedback` columns | 0-5 |
| alert type/severity/state/source/start/ack | `alerts_data` columns | |

## 7. Capability matrix (per tenant)

Legend: S supported, D derivable with caveat, U unsupported.

| Analysis | pinnacle-Slc | vanta-Sea | vanta-Aus | catalyst-Sac | orbit-Slc | Basis |
|---|:-:|:-:|:-:|:-:|:-:|---|
| Delayed-trip rate, delay reason mix | S | S | S | S | S | ride files |
| On-time pickup / drop rate (leg) | S | S | S | S | S | emp legs with both epochs |
| No-show and dashboard-cancellation rate | S | S | S | S | S | emp legs |
| Occupancy | S | S | S | S | S | ride files, cap at 100% |
| Vendor peer comparison | S | S | S (5 vendors) | S | S (3 vendors) | min volume 500 trips |
| Site × shift × direction contribution | S | S | D (single office) | S | S | office count |
| Cost per trip and spend trend | S | S | S | S | S | bill, negatives excluded |
| Cost per km | S | U | U | S | S | Q2 |
| Feedback low-rating rate | S | D (3.7% coverage) | D (3.9%) | D (11.6%) | S | Section 4 |
| Safety alert rate, Sev-1/2 rate | S | S | S | S | D (low volume) | alerts |
| Tracking coverage (device unreachable) | S | S | S | U (0 events) | U (0 events) | alerts by tenant |
| Escort compliance (women travelling alone) | D | S | S | U | U | alert types by tenant |
| EV share / sustainability | S | S | S | S (0% EV) | S | fuel type |
| GPS / location analysis | U | U | U | U | U | no coordinates |
| Budget variance | U | U | U | U | U | no budget |

## 8. Governed metric contracts v1

All metrics are computed per tenant, per window, with optional dimensions restricted to: vendor_id, site_id, shift_id, direction, mode, fuel_type, vehicle_id. Minimum volume for a ranked group is 300 trips (or 500 for vendor peer rankings) unless the contract states otherwise. Each result carries numerator, denominator, population, window, filters, data version and caveats.

| ID | Name | Numerator / denominator | Grain | Exclusions | Unit |
|---|---|---|---|---|---|
| M01 | Delayed-trip rate | trips with `delay_minutes > 0` / trips | trip | none | % |
| M02 | Mean and P90 delay of delayed trips | `delay_minutes` capped at 600 | trip | delay = 0; > 1,440 quarantined | min |
| M03 | Delay-reason mix | delayed trips by `delay_reason` / delayed trips | trip | none | % |
| M04 | On-time pickup rate | boarded legs with `actual_pickup - planned_pickup ≤ 10 min` / boarded legs with both epochs | leg | null epochs, not boarded | % |
| M05 | On-time drop rate | boarded legs with `actual_drop - planned_drop ≤ 10 min` / same | leg | as M04 | % |
| M06 | No-show rate | legs with `is_no_show` / planned legs (`signintype` not null or boarded) | leg | `stwid = 0` | % |
| M07 | Dashboard-cancellation rate | legs with `not_boarding_reason = TRIP_CANCELLED_FROM_DASHBOARD` / planned legs | leg | as M06 | % |
| M08 | Occupancy | `min(actualemployee_cnt, capacity)` / `actual_cab_capacity` | trip | capacity null | % |
| M09 | Cost per trip | sum `trip_cost` / billed trips | bill | `trip_cost < 0`, null trip_id, exact duplicates | currency |
| M10 | Cost per billed km | sum `trip_cost` / sum `total_trip_km` | bill | `total_trip_km = 0`, negatives | currency/km |
| M11 | Low-rating rate | legs rated with driver or cab or safety `≤ 2` / legs rated `> 0` | feedback | rating 0 | % |
| M12 | Mean driver / safety rating | mean of ratings `> 0` | feedback | rating 0 | 1-5 |
| M13 | Alert rate | alerts / trips × 1,000 | trip | `EMPLOYEE_SIGN_OFF_TIME_VIOLATION` (Q5) | per 1,000 trips |
| M14 | Sev-1/2 alert rate | alerts with severity in {Sev-1, Sev-2} / trips × 1,000 | trip | as M13 | per 1,000 trips |
| M15 | Alert acknowledgement P90 | `acknowledge_time - start_time` | alert | null severity, null ack | min |
| M16 | Tracking-gap rate | `DEVICE_NOT_REACHABLE` alerts / trips × 1,000 | trip | tenants with zero events → U | per 1,000 trips |
| M17 | EV share | trips with `Electric` / trips | trip | none | % |
| M18 | Escort compliance | trips with `actual_escort` among trips with a `WOMAN_TRAVELLING_ALONE` alert | trip | tenants without the alert type → U | % |

Comparison modes for every metric: prior 4 complete weeks (historical), same tenant other vendors/sites (peer), other tenants same window (cross-tenant peer, shown only to facilities-head persona), and the configured target where one is defined.

### Configured targets (not organizer-supplied)

| Metric | Default target | Label in UI |
|---|---|---|
| M01 | ≤ 10% delayed trips | "Configured target, editable per tenant" |
| M04 | ≥ 90% on-time pickups | same |
| M06 | ≤ 10% no-show | same |
| M15 | Sev-1/2 acknowledged within 5 min at P90 | same |

Targets live in tenant configuration with a version; the brief always states that the target is configured, never that the organizer supplied it.

## 9. Baseline facts (May-July 2026)

| Tenant | Trips | M01 May / Jun / Jul | M04 late-pickup share (>10 min) May / Jun / Jul | M06 May / Jun / Jul | Median cost per trip | EV share |
|---|---:|---|---|---|---:|---:|
| pinnacle-Slc | 251,774 | 12.3% / 16.5% / 11.7% | 18.0% / 20.3% / 16.8% | 0.8% / 1.0% / 1.4% | 1,020-1,145 | 0% |
| vanta-Sea | 180,064 | 8.7% / 13.6% / 11.4% | 2.2% / 3.0% / 2.9% | 16.3% / 14.0% / 9.2% | 1,390-1,416 | 17-19% |
| vanta-Aus | 70,199 | 0.8% / 3.3% / 4.1% | 3.2% / 5.4% / 6.6% | 13.0% / 12.5% / 8.0% | 1,200 | 27-35% |
| catalyst-Sac | 65,214 | 3.8% / 3.8% / 2.5% | 5.8% / 6.0% / 4.8% | 1.0% / 1.0% / 1.0% | 1,446-1,485 | 0% |
| orbit-Slc | 48,295 | 3.0% / 4.9% / 3.0% | 22.2% / 23.0% / 21.5% | 7.7% / 7.8% / 7.9% | 1,170-1,253 | 16-17% |

Dataset-wide: 30.8% of boarded legs are dropped more than 10 minutes late, 10.0% are picked up more than 10 minutes late, and the trip-level delay flag correlates 0.85 with drop deviation but only 0.05 with pickup deviation. The organizer flag therefore measures drop lateness; pickup punctuality must come from M04.

## 10. Golden anomalies selected from evidence

### G1 (primary): pinnacle-Slc morning login delay spike, week of 2026-06-01 to 2026-06-07

| Fact | Value | Evidence |
|---|---|---|
| Delayed-trip rate, week | 21.9% (4,357 of 19,913 trips) | M01 |
| Baseline, 2026-05-04 to 2026-05-31 | 12.3% | M01 historical |
| Excess delayed trips | ≈ 1,912 | derived |
| Employees on delayed trips that week | 7,780 rider legs; ≈ 3,414 excess | derived from `actualemployee_cnt` |
| LOGIN direction | 23.9% vs 11.0% baseline | M01 by direction |
| Clearwater Campus | 24.1% vs 12.9%; 51% of all delayed trips | M01 by site |
| Shifts 09:00-10:30 | 35.8% delayed; 37% of delayed trips | M01 by shift |
| Vendor dispersion | every vendor rose; range 17.1%-28.4% | M01 by vendor |
| Delay reasons among delayed trips | EMPLOYEE 48%, DRIVER 39%, TRAFFIC 13% (DRIVER up from 33%) | M03 |
| Peer tenants same week | vanta-Sea 17.3%, orbit-Slc 7.6%, catalyst-Sac 4.3%, vanta-Aus 1.8% | cross-tenant |
| Leg-level confirmation | late pickups > 10 min 22.0% vs 17.9% | M04 |
| Cost | median cost per trip fell 1,145 → 1,020 in June cycle; no cost penalty visible | M09 |
| Feedback | 93.5% coverage; low-rating rate flat at 0.4-0.5% | M11 |

Expected agent behaviour: the Supervisor plans vendor, site-shift, delay-reason, feedback and cost tasks; the Investigator finds the site-shift concentration and the all-vendor rise; the Evidence Critic rejects any single-vendor blame; the brief recommends a Clearwater Campus morning-shift watchlist plus an investigation ticket, not a vendor escalation. Simulated "as-of" date for the demo: 2026-06-08.

### G2 (secondary and degraded-data demo): vanta-Aus punctuality and experience deterioration through July

| Fact | Value | Evidence |
|---|---|---|
| Delayed-trip rate | 0.8% May → 3.2% prior 4 weeks → 7.6% in 2026-07-27 to 07-31 (4,654 trips) | M01 |
| Late pickups > 10 min | 3.2% May → 9.9% final week | M04 |
| Late drops > 10 min | 7% May → 24.4% final week | M05 |
| Low driver-rating rate | 2.7% May → 4.1% June → 4.2% July (coverage 3.9%) | M11, low coverage |
| Device-unreachable alerts | 327 → 1,058 → 948 per month | M16 |
| All five vendors deteriorated | Meera Pavlov and Priya Mikhailov worst on feedback | M01/M11 by vendor |
| No-show improved | 13.0% → 8.0% | M06 |
| EV share rose | 27% → 36% | M17 |
| Cost per km | unsupported (all billed km zero) | capability matrix |

Expected behaviour: the brief shows a cross-domain trend with explicit caveats (single office, low feedback coverage, cost-per-km unavailable) and moderate confidence. Simulated as-of date: 2026-08-01.

### G3 (false anomaly, must not escalate): pinnacle-Slc sign-off violation alerts

7,670 `EMPLOYEE_SIGN_OFF_TIME_VIOLATION` alerts in the two weeks of 2026-05-04 to 05-17, then near zero. A naive alert-rate detector would flag either the May spike or the June "drop". The detector must classify this as a data-regime change (single event type, single tenant, step change to zero) and surface it as a data-quality note, not an operational issue.

### Peer-comparison cases for the conversational drawer

- Pooja Mikhailov Travel: 17.2% delayed trips versus 13.7% pinnacle-Slc peer median; cost per km 99 versus 85 for the largest peer.
- Amit Volkov Travel: 70% of LOGOUT drops more than 10 minutes late.
- Vikram Mikhailov Travel (catalyst-Sac): 18.2 Sev-1/2 alerts per 1,000 trips versus under 7.3 for every other vendor; PANIC_DEVICE dominant.
- Meera Lebedev Travel: 43% delayed trips on 1,200 trips and -14.66M billing adjustments; a small-volume vendor that must be qualified, not ranked.

## 11. Golden evaluation cases derived from this dataset

Deterministic metric fixtures (hand-reconciled against the profile above):

1. M01 for pinnacle-Slc, 2026-06-01 to 06-07 = 4,357 / 19,913.
2. M01 baseline pinnacle-Slc, 2026-05-04 to 05-31 = 12.3%.
3. M04 for vanta-Aus final week = 9.9% late (> 10 min).
4. M09 for vanta-Sea May cycle excludes 158 negative lines and returns a positive median of 1,390.
5. M10 returns "unsupported" for vanta-Aus and vanta-Sea.
6. M13 excludes sign-off violation alerts and does not flag May for pinnacle-Slc.
7. Join on `trip_id` alone for orbit-Slc versus composite key differs by exactly 6,753 IDs.
8. Leg dedupe removes 708 rows; bill dedupe removes 72 exact duplicates.
9. Delay cap: Pooja Mikhailov Travel mean delay of delayed trips uses capped values; 77 trips above 600 min quarantined.
10. Marshal rating of 0 excluded from M12.

Corrupted variants for degraded-data tests (generated from the real files, never replacing them):

- V1: remove `emp_Data.csv` (no leg metrics, no no-show; trip-level only).
- V2: remove `bill_data.csv` (cost branch disabled).
- V3: shuffle 5% of `trip_id` in `trip_feedback.csv` (unmatched rows quarantined, coverage reported).
- V4: inject 2,000 duplicate ride rows across two tenants (dedupe report).
- V5: blank `severity` on all alerts (severity metrics disabled, alert counts remain).

Trajectory and narrative cases: G1 must produce a site-shift finding and reject vendor blame; G2 must carry three caveats; G3 must route to a data-quality note; a cross-tenant question from a transport-manager persona must be refused; a request for "cost per km for vanta-Aus" must return unsupported with the reason.

## 12. Scale and performance notes

- ~530 MB CSV, 615k trips, 1.6M legs. DuckDB loads the seven files in seconds and the daily tenant × site × shift × vendor aggregates fit in memory. Pre-compute daily metric snapshots per tenant on load; the scheduled run reads snapshots and touches raw tables only inside investigation tools with row limits.
- Trip and leg tables are the only large scans; alerts, feedback and bills are small enough to join on demand.
