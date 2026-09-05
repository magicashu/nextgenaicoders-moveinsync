# Mobility Decision Copilot — requirements and acceptance

Version 1.2 · 2026-09-05 · Selected design and consolidated requirements

Companion: [selected architecture](architecture.md). This is a build specification, not a claim of implementation completion or measured performance. The organizer's statement, explicit user decisions and approved data/metric contracts take precedence over examples in imported guides.

## 1. Purpose and outcomes

Turn the supplied mobility records into prioritized exceptions, contextual evidence, bounded recommendations and leadership-ready reports. Demonstrate sense → reason → act through a complete, observable workflow.

| ID | Outcome | Acceptance evidence |
|---|---|---|
| BO-01 | Reduce investigation effort | A manager can move from a prioritized issue to its contributors and evidence through a clear, short flow. |
| BO-02 | Contextualize facts | Each surfaced issue has an eligible historical, configured-target or authorized peer comparison, or states why the reference is unavailable. |
| BO-03 | Act proactively | A scheduled/configured historical run can detect and investigate a material issue without a user query. |
| BO-04 | Support a controlled decision | Verified findings lead to a policy-checked proposal, authorized approval, revalidation, mock effect and audit. |
| BO-05 | Support leadership | The same verified evidence produces a concise forwardable report with decisions, caveats and methodology. |
| BO-06 | Control latency and AI use | Deterministic calculations, bounded work, reusable evidence, provider limits and observable failures keep the workload inspectable. |

Primary persona: Transport Manager. The same evidence serves the Transport and Facilities Head through a leadership view. A separate Team/Line Manager experience is optional. The organizer requires at least one persona, not three complete applications.

## 2. Initial scope and conditional additions

| Initial delivery | Conditional or later |
|---|---|
| Official CSV ingestion, profile, capabilities and reconciliation | General historical ETL platform and arbitrary import formats |
| Required M01–M18 governed contracts, delivered in golden-path order | New metrics or changed formulas only through a versioned decision |
| Proactive issue detection and selective investigation | Optional clustering and richer incident follow-up |
| Four agent roles, eighteen main nodes, seven analytical workers | New agents only for a demonstrated independent responsibility |
| Exception workspace, evidence, approval, audit and leadership output | Replay controls, GPS map only with new supported location data |
| Contextual questions using the same governed tools | Generic unrestricted chatbot |
| Server-side optional Sarvam with deterministic fallback | Mandatory provider dependency or additional model infrastructure |
| Local runnable application; durable control-state option | Production identity federation, real vendor integrations, distributed streaming |
| Dataset quality counts and metric-specific caveats | Full persistent row-level quality ledger and analytical publication/rollback |
| Versioned metric registry and structured evidence | Document RAG only after corpus and retrieval gates pass |

No external vendor or employee communication is required. Initial actions are mock actions. Production-grade authentication is outside the organizer's expectation; server-side tenant authorization and governed action controls remain required.

## 3. Official dataset contract

The [official data and metric contracts](#official-data-and-metric-contracts) are authoritative. The plain-English introduction is [Understanding the problem statement](<Understanding the problem statement.md>).

| Domain | Official files | Data rows in the approved profile |
|---|---|---:|
| Trips | Three monthly files | 615,546 |
| Employee legs | One file | 1,637,906 |
| Feedback | One file | 512,873 |
| Alerts | One file | 51,699 |
| Billing | One file | 620,942 |
| Total | Seven CSV files, May–July 2026 | 3,438,966 |

These are reference observations to reconcile on import, not hard-coded runtime counters. Preserve original bytes under the configured official-data location. Synthetic fixtures remain separately labeled and never define business targets or unsupported real-data capabilities.

Required invariants:
- Canonical trip identity is the typed pair (business_unit, trip_id), never trip_id alone.
- Normalize documented thousands separators and validate identifiers; do not repair arbitrary malformed strings by keeping only their digits.
- Apply source-specific date and numeric parsing, monthly schema reconciliation and the approved wall-clock interpretation of source epochs. Keep UTC application audit timestamps distinct.
- Preserve data versions, checksums, parser/configuration versions and quality reasons.
- Distinguish invalid fields, rejected rows, unmatched child rows, duplicates and per-metric exclusions.
- Report both child-row match coverage and parent-trip participation with named numerators/denominators.
- Treat placeholder riders, billing adjustments, rating zeros, invalid severity and extreme delays according to the approved contracts.
- Use office × shift × direction as a route proxy. The files contain no GPS coordinates, route IDs, driver IDs, budget values or free-text feedback.

## 4. Governed business rules

This table maps requirements to the accepted contracts; it does not create another metric catalog. Exact eligibility, calculations, dimensions and exclusions remain in M01–M18 v1.1 and D-039.

| ID | Binding rule |
|---|---|
| BR-01 | Every request, join, evidence object, cache, action and audit is tenant-scoped. Cross-tenant comparisons require an explicitly authorized role and scope. |
| BR-02 | M01 is the trip-level delayed-trip rate using delay_minutes > 0. Its configured rate target is separate from that row-level definition. |
| BR-03 | M04/M05 measure employee-leg pickup/drop punctuality within ten minutes using the corresponding epochs and approved boarded-leg eligibility. Five-minute exploratory OTA is not a replacement. |
| BR-04 | M08 caps occupancy at capacity while retaining an explicit over-capacity quality flag and the raw source value. |
| BR-05 | M06 includes all valid non-placeholder employee legs after approved deduplication; do not exclude no-shows by requiring arrival or boarding. |
| BR-06 | M09 aggregates retained billing lines per composite trip, applies exclusions and reports the median eligible trip cost. M10 follows its billed-distance rule and per-tenant capability gate. |
| BR-07 | M11 is driver-only low-rating rate; M12 excludes non-positive ratings. Report the correct participation/rating coverage and low-coverage caveats. |
| BR-08 | M13/M14 exclude the sign-off event regime change; M15 is eligible Sev-1/2 acknowledgement P90. Preserve unknown-severity quality reasons. |
| BR-09 | Historical comparison uses the approved prior complete weeks; peer ranking preserves cohort and minimum-volume rules. Configuration changes are versioned. |
| BR-10 | M02 uses the approved cap/quarantine rules while preserving late flags. Do not change evidence through chart-only clipping or unapproved cleaning. |
| BR-11 | M16 is a tracking-alert proxy, M17 is EV trip share, and M18 is descriptive escort presence. No GPS, emissions or compliance guarantee follows from them. |
| BR-12 | Missing or unsupported metrics are explicit unavailable/partial results, never zero. Configured targets are labeled as configured, never organizer-supplied SLA values. |

## 5. Functional requirements

Each requirement needs an owner, a test or inspection criterion, and completion evidence in the team delivery plan.

### Data and quality

| ID | Requirement and acceptance |
|---|---|
| FR-DATA-01 | An authorized import/profile operation reads configured immutable sources and records file identity, versions, counts, duration and result. |
| FR-DATA-02 | Parsing handles known format drift without overwriting source bytes or silently coercing malformed identifiers. |
| FR-DATA-03 | All child joins use the composite trip key; cross-tenant collisions cannot attach records to another tenant. |
| FR-DATA-04 | Invalid fields and rows carry explicit reasons. Metric eligibility is evaluated separately so an invalid distance does not remove an otherwise valid observation from unrelated metrics. |
| FR-DATA-05 | Reconciliation names physical/parsed/accepted/rejected/duplicate/unmatched populations and avoids double-counting overlapping quality categories. |
| FR-DATA-06 | Repeating an import with the same content and configuration cannot duplicate accepted records. Document supported restart behavior rather than assuming arbitrary-step recovery. |

### Analytics and experience

| ID | Requirement and acceptance |
|---|---|
| FR-AN-01 | The workspace shows authorized scope, reporting period/as-of date, source freshness, governed readings and valid comparison context. |
| FR-AN-02 | Drill-down uses supported vendor, site, shift, direction, mode and date dimensions, with population, exclusions and capability reasons. |
| FR-AN-03 | Numeric answers come from governed parameterized tools. Neither the model nor browser reimplements metric arithmetic. |
| FR-AN-04 | Each displayed fact resolves to its metric/query definition, filters, data/metric version and supporting evidence. |
| FR-AN-05 | Charts use backend values with explicit units, denominators and warnings; partial or unavailable data is visible. |
| FR-AN-06 | Sustainability initially reports supported fuel/EV share. Do not infer emissions savings without a separately approved model and inputs. |

### Four-agent workflow

| ID | Requirement and acceptance |
|---|---|
| FR-AG-01 | The Supervisor proposes an allowed bounded investigation plan for a query or proactive issue. An optional server-side LLM may reason over a bounded user question/context treated as untrusted data and select typed allowlisted workers/metrics; deterministic fallback and validation preserve mandatory comparisons, scope and budget. |
| FR-AG-02 | The Investigator selects governed analytical workers, validates their structured results and stops within shared tool/depth/time limits. |
| FR-AG-03 | The Evidence Critic challenges unsupported interpretation and vendor blame; deterministic verification checks claims against the evidence actually cited. |
| FR-AG-04 | The Briefing/Action role selects and explains verified findings and drafts a bounded proposal. Deterministic services own policy and action state. |
| FR-AG-05 | Reporting produces operational and leadership output from the same verified facts, with required methodology and caveats. It is not an extra permanent agent. |
| FR-AG-06 | Insufficient evidence produces a limitation or bounded clarification; unsupported numbers, sources and causal claims are not invented. |
| FR-AG-07 | Safe retries and one correction cycle remain bounded. Failure, cancellation, partial evidence and fallback are visible; abandoned work is not blindly replayed. |
| FR-AG-08 | Runs retain identity/scope, versions, plan, safe tool records, evidence, usage, timings and approval outcome, with redaction. |

FR-AG-01 implementation boundary: planner input includes only a detector-selected issue, authorized run context and matching capability matrix. Its emitted requests use the run tenant and data version, a current versus prior-four-complete-week window, registered workers/metrics, and remaining tool-call allowance. Node 9 remains the final request validator.

### Questions, proactive work, actions and reports

| ID | Requirement and acceptance |
|---|---|
| FR-UX-01 | Contextual questions use the same authorization, metrics and evidence path as the proactive brief. |
| FR-UX-02 | Ask a focused clarification when ambiguity materially changes the requested result; otherwise use visible context. |
| FR-PR-01 | A configured historical schedule creates a scoped, deduplicated brief job and can complete without a manual query. |
| FR-PR-02 | Reuse is identity/version-aware. An optional incident persistence feature must distinguish issue identity, recurring detection and actual resolution. |
| FR-ACT-01 | Approval binds an authorized actor to an exact proposal and evidence version; fresh revalidation precedes an idempotent mock effect and receipt. |
| FR-ACT-02 | Rejected, expired, unauthorized or stale proposals cannot execute. Edits undergo policy and approval validation; every outcome is audited. |
| FR-REP-01 | Leadership output is forwardable printable HTML or an equivalent supported export with period, scope, findings, decisions, caveats and methodology. |

### Conditional knowledge requirements

FR-RAG-01 through FR-RAG-04 are reserved for a later document-dependent extension: authorized ingestion with provenance, tenant/ACL-filtered retrieval, SQL authority for structured facts, and document version retirement. They are not initial release blockers.

Activation requires a decision-relevant document corpus, at least five document-dependent golden questions, enforceable source/version/access metadata, evaluated retrieval and citation quality, and a measured resource budget. Generated narratives never override their original evidence.

## 6. Non-functional and AI controls

| ID | Requirement |
|---|---|
| NFR-PERF-01 | Measure dashboard/API latency under a documented machine, data and concurrency workload, separating cold/warm and cache-hit behavior. |
| NFR-PERF-02 | Measure queue admission, queue wait and fresh investigation completion separately. A progress stream is not proof of faster analysis. |
| NFR-PERF-03 | Record import time, peak memory and reconciliation results on official data; test restart/idempotency for explicitly supported failure paths. |
| NFR-SCALE-01 | Bound queues, worker pools, caches and provider concurrency; expose saturation and errors. Larger datasets and multiple replicas require separate workload evidence. |
| NFR-REL-01 | Durable mode preserves job/approval/action state and prevents duplicate claims/effects. Document recovery limits and fail visibly on unsafe replay. |
| NFR-REL-02 | Provider timeouts, bounded safe retries/cooldown, schema validation and deterministic fallback preserve usable outputs without external connectivity. |
| NFR-COST-01 | Send compact governed evidence, not raw fact tables; record provider attempts, real usage, configured cost estimates and fallback separately. |
| NFR-SEC-01 | Enforce tenant/resource authorization at API, tool, evidence, cache/reuse, action and audit boundaries. |
| NFR-SEC-02 | Keep provider keys server-side and outside committed files, browser payloads and logs. |
| NFR-SEC-03 | Deterministic role/policy checks govern data access, export, configuration and approval. |
| NFR-PRIV-01 | Minimize rider-level information; redact unnecessary identities from prompts, logs and reports. |
| NFR-OBS-01 | Correlate request, job, run, tool, data version and audit references. Keep business audit separate from optional diagnostic trace export. |
| NFR-A11Y-01 | Primary flows support keyboard operation, visible focus, sufficient contrast, text alternatives and reduced motion, with accessibility validation. |
| NFR-MAINT-01 | Components and four agent roles communicate through versioned typed boundaries; ownership is explicit. |
| NFR-PORT-01 | The provider and persistence adapters can change without changing metric authority, workflow policy or public facts. |

The incoming two-second/twelve-second p95 figures and ten-million-row goal are proposed benchmark experiments, not guaranteed release claims. Adopt numeric operational SLOs only with a named workload, owner and measurement; do not infer performance from an architecture diagram.

AI controls AI-01–AI-09 remain mandatory: treat retrieved/data content as untrusted; allowlist and validate tools; prohibit arbitrary generated SQL; require approval for external effects; distinguish hypotheses from facts; restrict sensitive-segment use; test injection and tenant/approval bypass; version prompts/models/evidence; and retain non-LLM operation.

## 7. Release acceptance

Implementation increment D-057–D-059: all four runtime roles must use a shared governed analytics authority, enforce current/reference request fidelity and bounded process-wide resources, and preserve canonical claim values and caveats. DS-01–DS-20 are the business acceptance scenarios. Index/aggregate choices must retain exact M01–M18 semantics; scaling evidence must identify row counts, concurrency, hardware, cold/warm state and limitations. Role skills are development guidance, not authorization or extra runtime agents.

Aggregate clarification D-060: M08 group/window values are capacity-weighted ratios of summed eligible capped occupied seats to summed eligible capacity; invalid capacity or occupancy remains a quality exclusion. M03 selects a named reason, and explicit M02/M12 variants identify statistic/rating dimension. Billing-window queries use complete source cycles without invented daily allocation. Oracle tests must assert these definitions.

Use the existing 30–50-case evaluation range with all risk categories represented. Deterministic tests decide numerical correctness, authorization, evidence validity and action behavior. Reviewed semantic rubrics may supplement them; a model judge cannot pass a release alone.

| ID | Gate |
|---|---|
| AC-01 | Official-data metric fixtures reproduce M01–M18 semantics and approved rounding/eligibility, rather than only synthetic examples. |
| AC-02 | Every displayed factual claim is supported by its cited evidence, scope, units, dates and version. Unsupported claims are suppressed or explicitly bounded hypotheses. |
| AC-03 | Cross-tenant joins, resource lookups, cache reuse and unauthorized cross-tenant questions do not leak data. |
| AC-04 | No action bypasses policy, valid approval, fresh revalidation, idempotency and audit; rejection/expiry/edit/duplicate attempts are covered. |
| AC-05 | G1 reaches a site/shift explanation, rejects unsupported single-vendor blame and completes a watchlist/investigation mock-action flow. |
| AC-06 | G2 carries its low-coverage and unsupported-cost-per-km caveats; G3 produces a data-quality note and does not escalate. |
| AC-07 | One official-data proactive flow, contextual question, approval/receipt flow and leadership report work end to end. |
| AC-08 | Relevant data, workflow, security, provider-failure, recovery and UI tests pass, with performance results labeled by workload and mode. |

Retain the approved corrupted-data cases: missing legs, missing bills, shuffled feedback keys, duplicate trips and unavailable alert severity. Generate them as copies; do not mutate the official source. Add saturation, cancellation, stale-evidence and provider-fallback tests where those behaviors are implemented.

## 8. Delivery and traceability

Freeze shared records and their producing/consuming owners before parallel work. Use the [team ownership](architecture.md#java-scaffold-and-team-ownership) plus a designated integration owner; combine packages for a smaller team.

For each task record requirement ID → component owner → input/output contract → dependency → acceptance evidence. Build the complete G1 path before replay or retrieval. Keep human product/release review concise and concrete; imported process documents do not introduce new blanket permission gates.

## 9. Decisions settled for this build

| Former open question | Selected decision |
|---|---|
| Five-minute OTA | Retain separate M01 and M04/M05 contracts; exploratory OTA is not the headline. |
| Rating-zero semantics | Use the approved rating contracts and show the correct coverage. |
| Number of agents | Four roles with an explicit critic and deterministic verification. |
| Browser/backend runtime | React/TypeScript and Java/Spring Boot; no Streamlit/Python or Angular migration by default. |
| Analytical/control storage | DuckDB analytics; PostgreSQL durable control with explicit local fallback. |
| Sarvam | Optional server-side adapter, contract validation, bounded use and deterministic fallback. |
| RAG and replay | Conditional additions after the golden path, not initial dependencies. |
| External communications | Mock actions only for the prototype. |

Use this file for all requirements, data rules and acceptance gates; architecture.md defines structure and ownership, agents-guide.md explains the four agents, and Understanding the problem statement.md provides the plain-English walkthrough.

## Official data and metric contracts

The following approved profile values are reference observations for the team to reproduce, not results produced by this scaffold. Metric-contract v1.1 and its explicit exclusions take precedence over older exploratory definitions. Sources remain immutable; do not invent missing capabilities. The field map, quality rules, metric catalog, configured targets and golden cases now live here instead of separate documents.

### Inventory and checksums

The shared dataset is in `outputs/MoveInSync - Anonymised Trip-Log Dataset/`. Its seven CSV files are stored with Git LFS; run `git lfs pull` after cloning with Git LFS installed. The accompanying `Dictionary/` contains the supplied field descriptions. The earlier self-generated dataset has been removed from this branch; approved contracts and original-file checksums remain unchanged.

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

### Tenant and key model

- **Tenant = `business_unit`.** Five tenants: `pinnacle-Slc` (251,774 trips), `vanta-Sea` (180,064), `vanta-Aus` (70,199), `catalyst-Sac` (65,214), `orbit-Slc` (48,295). Vendors and offices are shared across tenants, so every metric, cache, evidence ID and action must carry the tenant.
- **Trip key = `(business_unit, trip_id)`.** `trip_id` ranges overlap between `orbit-Slc` (1,208,678-1,368,372) and `vanta-Aus` (1,097,349-1,260,162). Within a tenant `trip_id` is unique in the ride files (0 duplicates).
- **Leg identity:** carry (business_unit, trip_id, stwid). M06 uses exact deduplication under metric-contract v1.1; do not treat every non-identical repeated leg key as an exact duplicate. Reconcile duplicate counts during the data-owner implementation.
- **Rider key = `stwid`**, with `0` treated as "no rider" (1,414 leg rows, most alerts).
- **Bill key** = `(business_unit, trip_id)` with 157 duplicate lines, 72 of them exact duplicates. Exact duplicates are dropped; the remaining 85 are kept and flagged as multi-line bills.
- **Normalisation rules per file:** strip thousands separators from `trip_id`, `stwid`, `*_epoch`, `delay_minutes`, `trip_cost`; parse `trip_date` per file format; cast `is_driver_nc`/`is_cab_nc` from `true/false/True/False/null` to nullable boolean; parse `planned_km` as float after stripping one comma-formatted value in July.
- **Time zone:** epochs interpreted as UTC agree with `trip_date` on 99.98% of trips and with `shift_type` hours. Treat epochs as already-local wall-clock and do no conversion.

### Join coverage

| Join | Coverage | Notes |
|---|---:|---|
| emp leg → trip | 100% both ways | Every trip has at least one leg |
| bill → trip | 99.0% of bill lines match; 99.9% of trips billed | 160 bill lines have null `trip_id` |
| feedback → trip | 99.97% of feedback rows match | Only 49.5% of trips have any feedback |
| alerts → trip | 99.1% of alerts match | Only 5.5% of trips have an alert |

Feedback coverage by tenant: `orbit-Slc` 95.6%, `pinnacle-Slc` 93.5%, `catalyst-Sac` 11.6%, `vanta-Aus` 3.9%, `vanta-Sea` 3.7%. Feedback conclusions for the last three tenants must be marked low-coverage.

### Data-quality findings beyond the organizer README

| # | Finding | Scale | Handling rule |
|---:|---|---:|---|
| Q1 | `trip_id` collides across tenants | 6,753 IDs | Composite key; never join on `trip_id` alone |
| Q2 | Billed `total_trip_km` has unreliable coverage | Predominantly missing/nonpositive for `vanta-Aus` and `vanta-Sea`; the earlier absolute-zero assertion is superseded by current source review | Cost-per-km remains unsupported for those tenants; cost-per-trip still supported |
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

### Canonical field map

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

### Capability matrix (per tenant)

Legend: S supported, D derivable with caveat, U unsupported.

| Analysis | pinnacle-Slc | vanta-Sea | vanta-Aus | catalyst-Sac | orbit-Slc | Basis |
|---|:-:|:-:|:-:|:-:|:-:|---|
| Delayed-trip rate, delay reason mix | S | S | S | S | S | ride files |
| On-time pickup / drop rate (leg) | S | S | S | S | S | emp legs with both epochs |
| No-show and dashboard-cancellation rate | S | S | S | S | S | emp legs |
| Occupancy | S | S | S | S | S | ride files, cap at 100% |
| Vendor peer comparison | S | S | S (5 vendors) | S | S (3 vendors) | min volume 500 trips |
| Site × shift × direction contribution | S | S | D (office concentration) | S | S | observed office count and group populations |
| Cost per trip and spend trend | S | S | S | S | S | bill, negatives excluded |
| Cost per km | S | U | U | S | S | Q2 |
| Feedback low-rating rate | S | D (3.7% coverage) | D (3.9%) | D (11.6%) | S | Section 4 |
| Safety alert rate, Sev-1/2 rate | S | S | S | S | D (low volume) | alerts |
| Tracking coverage (device unreachable) | S | S | S | U (0 events) | U (0 events) | alerts by tenant |
| Escort presence (women travelling alone) | D | S | S | U | U | alert types by tenant |
| EV share / sustainability | S | S | S | S (0% EV) | S | fuel type |
| GPS / location analysis | U | U | U | U | U | no coordinates |
| Budget variance | U | U | U | U | U | no budget |

### Governed metric contracts v1.1

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

#### Configured targets (not organizer-supplied)

| Metric | Default target | Label in UI |
|---|---|---|
| M01 | ≤ 10% delayed trips | "Configured target, editable per tenant" |
| M04 | ≥ 90% on-time pickups | same |
| M06 | ≤ 10% no-show | same |
| M15 | Sev-1/2 acknowledged within 5 min at P90 | same |

Targets live in tenant configuration with a version; the brief always states that the target is configured, never that the organizer supplied it.

### Golden anomalies selected from evidence

#### G1 (primary): pinnacle-Slc morning login delay spike, week of 2026-06-01 to 2026-06-07

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

#### G2 (secondary and degraded-data demo): vanta-Aus punctuality and experience deterioration through July

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
| Cost per km | unsupported (unreliable billed-km coverage) | capability matrix Q2 |

Expected behaviour: the brief shows a cross-domain trend with explicit caveats (office concentration, low feedback coverage, cost-per-km unavailable). Simulated as-of date: 2026-08-01. Current DuckDB reconciliation finds 69,801 vanta-Aus trips at Cedar Ridge Office and 398 at Santa Clara Office; the earlier single-office assumption is invalid. Do not assign a numerical or qualitative confidence score without an approved calibration rule.

#### G3 (false anomaly, must not escalate): pinnacle-Slc sign-off violation alerts

7,670 `EMPLOYEE_SIGN_OFF_TIME_VIOLATION` alerts in the two weeks of 2026-05-04 to 05-17, then near zero. A naive alert-rate detector would flag either the May spike or the June "drop". The detector must classify this as a data-regime change (single event type, single tenant, step change to zero) and surface it as a data-quality note, not an operational issue.

#### Peer-comparison cases for the conversational drawer

- Pooja Mikhailov Travel: 17.2% delayed trips versus 13.7% pinnacle-Slc peer median; cost per km 99 versus 85 for the largest peer.
- Amit Volkov Travel: 70% of LOGOUT drops more than 10 minutes late.
- Vikram Mikhailov Travel (catalyst-Sac): 18.2 Sev-1/2 alerts per 1,000 trips versus under 7.3 for every other vendor; PANIC_DEVICE dominant.
- Meera Lebedev Travel: 43% delayed trips on 1,200 trips and -14.66M billing adjustments; a small-volume vendor that must be qualified, not ranked.

### Golden evaluation cases derived from this dataset

Deterministic metric fixtures (hand-reconciled against the profile above):

1. M01 for pinnacle-Slc, 2026-06-01 to 06-07 = 4,357 / 19,913.
2. M01 baseline pinnacle-Slc, 2026-05-04 to 05-31 = 12.3%.
3. M04 for vanta-Aus final week = 9.9% late (> 10 min).
4. M09 for vanta-Sea May cycle excludes negative lines and returns a positive median of 1,390.34 (1,390 at whole-currency display precision), independently reconciled against the source CSV in DuckDB.
5. M10 returns "unsupported" for vanta-Aus and vanta-Sea.
6. M13 excludes sign-off violation alerts and does not flag May for pinnacle-Slc.
7. Join on `trip_id` alone for orbit-Slc versus composite key differs by exactly 6,753 IDs.
8. Leg dedupe removes 708 rows; bill dedupe removes 72 exact duplicates.
9. Delay cap: Pooja Mikhailov Travel mean delay of delayed trips caps 77 trips above 600 minutes; values above 1,440 minutes are quarantined rather than averaged.
10. Marshal rating of 0 excluded from M12.

Corrupted variants for degraded-data tests (generated from the real files, never replacing them):

- V1: remove `emp_Data.csv` (no leg metrics, no no-show; trip-level only).
- V2: remove `bill_data.csv` (cost branch disabled).
- V3: shuffle 5% of `trip_id` in `trip_feedback.csv` (unmatched rows quarantined, coverage reported).
- V4: inject 2,000 duplicate ride rows across two tenants (dedupe report).
- V5: blank `severity` on all alerts (severity metrics disabled, alert counts remain).

Trajectory and narrative cases: G1 must produce a site-shift finding and reject vendor blame; G2 must carry three caveats; G3 must route to a data-quality note; a cross-tenant question from a transport-manager persona must be refused; a request for "cost per km for vanta-Aus" must return unsupported with the reason.
