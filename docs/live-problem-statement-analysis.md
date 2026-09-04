# Live Problem Statement Analysis

Source reviewed: `/Users/miniorange/Downloads/problem_explanation_7qdzf3jxklt.pdf` (4 pages, visually inspected in full on 2026-09-04).

> Dataset update (2026-09-04): the official dataset has been received and profiled. Assumptions below that referred to GPS, rosters, routes or SLA fields are replaced by the bindings in [Dataset Profile and Capability Matrix](./dataset-profile-and-capability-matrix.md) and decisions D-029 through D-033.

## Executive decision

Build **Mobility Decision Copilot: a proactive operations briefing and intervention agent** over the supplied structured trip logs.

The product will detect what changed, benchmark it, investigate likely drivers, quantify impact, propose bounded actions, and produce two outputs from the same evidence:

1. An operational exception brief for the transport manager.
2. A leadership-ready daily/weekly narrative for the transport and facilities head.

This is not a generic chatbot, passive dashboard, document-RAG application, or route optimizer.

## Authoritative requirements extracted from the live statement

### Problem and users

- Domain: enterprise mobility and operations intelligence.
- Data domains: trips, vendors, drivers, employees, modes, GPS traces, delays, costs, safety/compliance, sustainability, employee feedback, and vendor performance.
- Named personas: transport manager; transport and facilities head; team/line manager.
- Pain: managers assemble static reports instead of acting on signals; isolated metrics lack historical/SLA/peer context.

### Mandatory

- Working end-to-end prototype on the supplied dataset.
- Agentic behavior that senses, reasons, and acts; not a passive dashboard or query-only tool.
- Serve at least one named persona.
- Contextualize metrics against at least one of historical trend, SLA/goal, industry benchmark, or peer comparison.

### Good-to-have and bonus

- Combine at least two output forms.
- Handle messy/missing data such as GPS gaps, unmatched records, and incomplete rosters.
- Proactive triggers, not only on-demand responses.
- Credible enterprise deployment story covering multi-tenancy, latency, and cost.
- Leadership-ready output that can be forwarded without rework.

### Explicitly not expected

- Production-grade authentication/security.
- A full historical data pipeline.
- Integration with real vendor systems.

### Evaluation weights

| Criterion | Weight | Winning proof |
|---|---:|---|
| Business impact and experience | 35 | Prioritized issue with affected trips/employees, quantified cost/SLA impact, clear recommended action, and a forwardable leadership brief |
| Functionality | 25 | A reliable live run on the provided dataset, including degraded/missing-data behavior |
| Agentic design and cost at scale | 20 | Scheduled sensing, selective investigation, bounded model calls, cached deterministic metrics, visible latency and cost |
| Architecture and code quality | 20 | Typed Java workflow, deterministic analytics, adapter-based ingestion, tests, traces, README, and AWS-aligned deployment diagram |

## Frozen implementation for this challenge

```text
Dataset adapter (CSV/JSON/Parquet)
        ↓
DuckDB analytical layer + versioned metric contracts
        ↓
LangGraph4j adapter or deterministic Java state machine
        ↓
Langfuse traces and evaluation
        ↓
Spring Boot + Spring AI
        ↓
React + TypeScript decision-support interface
```

PostgreSQL remains the production architecture target for audit/configuration and may replace DuckDB if setup is already available. DuckDB is the default demo analytics engine because the resource is a sample dataset and the deadline is immediate.

The backend runtime is Java 21 because the live statement explicitly prefers Java while remaining non-restrictive. The team has selected React with TypeScript for the interface because delivery confidence matters more than the non-mandatory Angular preference. LangGraph4j is used only if the focused routing, parallelism, approval-resume, serialization and tracing spike passes; otherwise the same typed nodes run through a small project-owned Java state machine.

## Agent graph

```text
START / scheduled trigger
        ↓
Dataset profile and quality gate (deterministic)
        ↓
Governed metric computation (deterministic)
        ↓
Benchmark and anomaly detection (deterministic)
        ↓
Issue prioritizer (deterministic score + LLM explanation)
        ↓
Investigation planner
        ├── vendor contribution query
        ├── site/shift/direction contribution query
        ├── delay-reason mix query
        ├── cost and billing query
        ├── feedback low-rating query
        ├── tracking and safety alert query
        └── no-show and roster query
        ↓
Evidence verifier
        ├── insufficient/conflicting → qualify or stop
        └── sufficient → action proposal
        ↓
Operational alert + leadership narrative
        ↓
Optional mock approval for communication/escalation
        ↓
Audit event
        ↓
END
```

The LLM chooses bounded investigative tools and writes explanations. Metric formulas, anomaly calculations, authorization, impact calculations, thresholds, and action execution remain deterministic.

## Primary golden-path story

Golden path G1 (D-032), tenant `pinnacle-Slc`, brief generated as-of 2026-06-08. Every number below was measured from the official files and must be reproduced by the DuckDB layer before display:

> Delayed trips for pinnacle-Slc reached 21.9% last week (4,357 of 19,913 trips), up from a 12.3% May baseline and above the configured 10% target. The rise is concentrated in morning LOGIN trips (23.9% vs 11.0%), at Clearwater Campus (24.1%, 51% of all delayed trips) and in the 09:00-10:30 shifts (35.8%). All vendors rose together, so this is a site and shift pattern rather than a single-vendor failure. About 1,900 excess delayed trips affected roughly 3,400 rider legs; leg-level late pickups confirm the trend (22.0% vs 17.9%). Cost per trip did not rise and feedback is flat, with 93.5% rating coverage. Confidence is high. Recommend placing Clearwater Campus morning shifts on a one-week watchlist and opening an investigation ticket; a leadership summary is ready for approval.

Secondary path G2, tenant `vanta-Aus`, as-of 2026-08-01, shows a slow cross-domain deterioration (delayed trips 0.8% → 7.6%, late pickups 3.2% → 9.9%, low driver ratings 2.7% → 4.2%, device-unreachable alerts tripled) with explicit caveats: single office, 3.9% feedback coverage, cost per km unavailable. Negative case G3 shows the detector classifying the pinnacle-Slc sign-off-violation alert step change as a data-regime change rather than an issue.

Together these demonstrate proactive sensing, benchmarking, cross-domain investigation, impact, graceful uncertainty, action, and reporting on the real data.

## Proactive trigger design

Run on dataset load and on a simulated schedule. Trigger only when a governed rule is met, for example:

- Delayed-trip rate (M01) or late-pickup rate (M04) above the configured target and at least 3 percentage points and 25% relative above the prior four complete weeks, with at least 300 trips.
- Vendor deterioration exceeds an absolute and volume-aware threshold (500 trips).
- Cost per trip rises while punctuality falls.
- Sev-1/2 alert rate doubles, or device-unreachable alert rate doubles.
- Low-rating rate rises where feedback coverage is adequate.
- A step change confined to one alert type is routed to a data-quality note, not an issue (G3).

Use explicit minimum-volume and materiality conditions to avoid noisy alerts. Rank alerts by estimated affected employees, SLA gap, cost impact, safety severity, confidence, and persistence.

## Data-quality behavior

Before analysis, profile schema, row counts, nulls, duplicates, key uniqueness, time range, timezone, categorical values, join coverage, coordinate validity, and numeric ranges.

Required graceful behaviors:

- No GPS: location analysis is marked unsupported; tracking gaps are proxied by device-unreachable alerts and never used for location conclusions.
- Cross-tenant `trip_id` collisions: join only on `(business_unit, trip_id)` and prove it with the 6,753-ID fixture.
- Unmatched records: quarantine rows, report join coverage, and compute clearly labeled matched-population metrics where valid.
- Low feedback coverage (3.7% to 11.6% for three tenants): show coverage and lower confidence; never generalise.
- Zero billed km (`vanta-Aus`, `vanta-Sea`): cost per km unsupported; cost per trip remains.
- Negative and extreme values: exclude negative bills as adjustments, cap delays at 600 minutes, quarantine above 1,440, null negative distances.
- Duplicate trips and legs: apply the documented keys and report removed rows (72 exact bill duplicates, 708 duplicate legs).
- Missing SLA: use configured targets labelled as such plus historical and peer comparison; never claim an organizer target.
- Sparse vendor/site: suppress or qualify rankings below minimum volume.
- Alert configuration changes: classify single-type step changes as data-regime changes.

## Dataset adapter contract

Do not bind business logic to raw column names. The canonical field map for the official files is Section 6 of the profile document. Populated canonical groups:

- IDs: tenant (`business_unit`), trip (`business_unit` + `trip_id`), rider (`stwid`), vendor, vehicle plate, site (`office`), shift (`shift_type`), direction.
- Time: planned/actual trip start and end, planned/actual pickup and drop per leg (epoch seconds, already local).
- Service: mode, delay minutes and reason, boarding status, no-show and cancellation reason, sign-in type, escort presence, driver/cab non-compliance.
- Economics: billed cost, billed km, contract, slab, billing cycle, capacity, planned/traveled km.
- Experience/safety: route, driver, cab, safety and marshal ratings; alert type, severity, state, source, start and acknowledgement times.
- Absent: GPS coordinates, driver ID, route ID, SLA target, budget, free-text feedback.

The application must start even when optional files are absent and publish the per-tenant capability matrix showing which analyses are enabled and why others are not.

## Governed metrics v1

Frozen as M01-M18 in Section 8 of the profile document (D-031):

- M01 delayed-trip rate (headline), M02 mean/P90 delay of delayed trips (capped), M03 delay-reason mix.
- M04 on-time pickup rate and M05 on-time drop rate within 10 minutes at leg grain.
- M06 no-show rate and M07 dashboard-cancellation rate.
- M08 occupancy.
- M09 cost per trip and M10 cost per billed km (unsupported for two tenants).
- M11 low-rating rate and M12 mean driver/safety rating.
- M13 alert rate, M14 Sev-1/2 alert rate, M15 acknowledgement P90, M16 tracking-gap rate, M18 escort compliance.
- M17 EV share.

Targets are configured per tenant and labelled as configured. Every output includes metric version, grain, filters, population, freshness/time range, and data-quality caveats.

## Evaluation plan before labels are known

Create tests at three levels:

1. **Metric fixtures:** the ten hand-reconciled fixtures from the real data plus small edge-case rows.
2. **Agent trajectories:** expected tool sequence, bounded steps, abstention, and approval routing.
3. **Narrative assertions:** every number exists in evidence; comparisons name their reference; unsupported causal claims are rejected; missing data is disclosed.

Golden cases G1-G3, corrupted variants V1-V5 and the trajectory/narrative cases are defined in Section 11 of the profile document (D-033); extend toward 20-30 cases during the build.

## Demo sequence

1. Load the official dataset and show the automatic quality/capability summary (composite key, 6,753 collisions caught, per-tenant capability matrix).
2. Show the proactive morning brief for pinnacle-Slc as-of 2026-06-08 without typing a question (G1).
3. Open the delayed-trip anomaly with its May baseline, configured target and peer tenants.
4. Expand the investigation to vendor, site-shift-direction, delay-reason, cost, feedback and safety-alert evidence; show the Critic rejecting vendor blame.
5. Show the watchlist and investigation-ticket recommendation, affected rider legs, confidence, and the leadership-ready output.
6. Approve the mocked action; show revalidation, idempotency and the audit event.
7. Open the Langfuse trace.
8. Switch to vanta-Aus as-of 2026-08-01 (G2) for caveats and unsupported metrics, then show G3 classified as a data-regime change.

## Scope cuts

- Defer hybrid RAG and reranking because the statement currently provides structured trip logs only.
- Reject OpenKB/PageIndex unless long documents are later supplied.
- Do not build a free-form text-to-SQL agent; expose bounded analytical tools backed by governed queries.
- Do not build production authentication, a full ETL platform, real vendor integration, route optimization, or autonomous external communication.
- Do not use a large agent swarm. Use one controlled investigation workflow with narrowly scoped tools.

## Dataset-arrival intake (completed 2026-09-04)

Executed as below; results in `dataset-profile-and-capability-matrix.md` and D-029 through D-033.

1. Preserve the original file and calculate a checksum.
2. Inventory files, formats, sheets/tables, sizes, encodings, and relationships.
3. Generate the schema and data-quality profile.
4. Map source columns to canonical fields and document unresolved meanings.
5. Inspect timestamps, units, SLA definitions, status enumerations, IDs, and denominators.
6. Compute baseline metrics independently and reconcile sample rows manually.
7. Identify the strongest real anomaly and make it the golden demo story.
8. Update metric contracts, evaluation cases, feature capability matrix, and decision register.
9. Freeze a clean demo subset plus messy-data variants while keeping calculations faithful to the provided data.

## Submission checklist

- Source repository.
- Architecture diagram.
- README and one-command setup.
- Sample inputs and outputs.
- Presentation deck.
- Live demo.
- Backup demo video if requested or prudent.
- Visible cost/latency and enterprise deployment story.
- Requirement-to-proof matrix and evaluation results.
