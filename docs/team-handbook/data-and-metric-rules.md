# Data and metric rules for the team

These are the approved dataset observations and analytical definitions for the build. Reproduce the golden values from the supplied data before showing them in the product. Do not invent missing fields, formulas, targets or thresholds. Counts below are reference observations, not evidence that a new implementation has passed validation.

## What the dataset does not contain

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


## Tenant and key model

- **Tenant = `business_unit`.** Five tenants: `pinnacle-Slc` (251,774 trips), `vanta-Sea` (180,064), `vanta-Aus` (70,199), `catalyst-Sac` (65,214), `orbit-Slc` (48,295). Vendors and offices are shared across tenants, so every metric, cache, evidence ID and action must carry the tenant.
- **Trip key = `(business_unit, trip_id)`.** `trip_id` ranges overlap between `orbit-Slc` (1,208,678-1,368,372) and `vanta-Aus` (1,097,349-1,260,162). Within a tenant `trip_id` is unique in the ride files (0 duplicates).
- **Leg key = `(business_unit, trip_id, stwid)`.** 708 duplicate legs exist; keep the first by planned pickup and report the count.
- **Rider key = `stwid`**, with `0` treated as "no rider" (1,414 leg rows, most alerts).
- **Bill key** = `(business_unit, trip_id)` with 157 duplicate lines, 72 of them exact duplicates. Exact duplicates are dropped; the remaining 85 are kept and flagged as multi-line bills.
- **Normalisation rules per file:** strip thousands separators from `trip_id`, `stwid`, `*_epoch`, `delay_minutes`, `trip_cost`; parse `trip_date` per file format; cast `is_driver_nc`/`is_cab_nc` from `true/false/True/False/null` to nullable boolean; parse `planned_km` as float after stripping one comma-formatted value in July.
- **Time zone:** epochs interpreted as UTC agree with `trip_date` on 99.98% of trips and with `shift_type` hours. Treat epochs as already-local wall-clock and do no conversion.


## Canonical field map

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


## Capability matrix (per tenant)

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


## Governed metric contracts v1.1

All metrics are computed per tenant, per window, with optional dimensions restricted to: vendor_id, site_id, shift_id, direction, mode, fuel_type, vehicle_id. Minimum volume for a ranked group is 300 trips (or 500 for vendor peer rankings) unless the contract states otherwise. Each result carries numerator, denominator, population, window, filters, data version and caveats.

| ID | Name | Numerator / denominator | Grain | Exclusions | Unit |
|---|---|---|---|---|---|
| M01 | Delayed-trip rate | trips with `delay_minutes > 0` / trips | trip | none | % |
| M02 | Mean and P90 delay of delayed trips | `delay_minutes` capped at 600 | trip | delay = 0; > 1,440 quarantined | min |
| M03 | Delay-reason mix | delayed trips by `delay_reason` / delayed trips | trip | none | % |
| M04 | On-time pickup rate | boarded legs with `actual_pickup_epoch - planned_pickup_epoch ≤ 10 min` / boarded legs with both pickup epochs | leg | either pickup epoch null, `boarding_status != Boarded`, `stwid = 0` | % |
| M05 | On-time drop rate | boarded legs with `actual_drop_epoch - planned_drop_epoch ≤ 10 min` / boarded legs with both drop epochs | leg | either drop epoch null, `boarding_status != Boarded`, `stwid = 0` | % |
| M06 | No-show rate | valid employee legs with `is_no_show = true` / all valid employee legs | leg | `stwid = 0`; exact duplicate legs | % |
| M07 | Dashboard-cancellation rate | legs with `not_boarding_reason = TRIP_CANCELLED_FROM_DASHBOARD` / planned legs | leg | as M06 | % |
| M08 | Occupancy | `min(actualemployee_cnt, capacity)` / `actual_cab_capacity` | trip | capacity null | % |
| M09 | Median billed cost per trip | median of per-trip positive billed cost after summing retained lines by `(business_unit, trip_id)` | bill/trip | `trip_cost < 0`, null trip_id, exact duplicates | currency |
| M10 | Cost per billed km | sum `trip_cost` / sum `total_trip_km` | bill | `total_trip_km = 0`, negatives | currency/km |
| M11 | Low driver-rating rate | feedback rows with `driver_rating` in {1, 2} / feedback rows with `driver_rating > 0` | feedback | driver rating 0; `stwid = 0`; exact duplicates | % |
| M12 | Mean driver / safety rating | mean of ratings `> 0` | feedback | rating 0 | 1-5 |
| M13 | Alert rate | alerts / trips × 1,000 | trip | `EMPLOYEE_SIGN_OFF_TIME_VIOLATION` (Q5) | per 1,000 trips |
| M14 | Sev-1/2 alert rate | alerts with severity in {Sev-1, Sev-2} / trips × 1,000 | trip | as M13 | per 1,000 trips |
| M15 | Sev-1/2 alert acknowledgement P90 | P90 of `acknowledge_time - start_time` for severity in {Sev-1, Sev-2} | alert | other/invalid/null severity, null/negative acknowledgement duration, M13 excluded event type | min |
| M16 | Tracking-gap rate | `DEVICE_NOT_REACHABLE` alerts / trips × 1,000 | trip | tenants with zero events → U | per 1,000 trips |
| M17 | EV share | trips with `Electric` / trips | trip | none | % |
| M18 | Escort-present rate | distinct trips with `actual_escort = true` among distinct trips with a `WOMAN_TRAVELLING_ALONE` alert | trip | tenants without the alert type → U; no compliance claim without an external rule | % |

For G1 vendor-trend language, “every vendor rose” means every vendor with at least 500 trips in both the current and baseline windows increased. Lower-volume vendors may be shown as qualified context but are not included in the universal statement.

Comparison modes for every metric: prior 4 complete weeks (historical), same tenant other vendors/sites (peer), other tenants same window (cross-tenant peer, shown only to facilities-head persona), and the configured target where one is defined.

### Configured targets (not organizer-supplied)

| Metric | Default target | Label in UI |
|---|---|---|
| M01 | ≤ 10% delayed trips | "Configured target, editable per tenant" |
| M04 | ≥ 90% on-time pickups | same |
| M06 | ≤ 10% no-show | same |
| M15 | Sev-1/2 acknowledged within 5 min at P90 | same |

Targets live in tenant configuration with a version; the brief always states that the target is configured, never that the organizer supplied it.


## Golden anomalies selected from evidence

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
