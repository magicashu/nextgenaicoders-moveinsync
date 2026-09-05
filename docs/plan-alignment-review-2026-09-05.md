# plan.md alignment review — 2026-09-05

Recommendation: retain its evidence and publication ideas, correct the data/control-plane defects, and translate it into an incremental plan for the existing Java application. This is a review, not authorization for a replacement architecture. No application code or supplied dataset was changed.

## Findings by impact

### 1. Critical: the global trip-key assumption fails on our data

At plan.md lines 529, 567 and 1292, the plan requires global trip uniqueness and one child aggregate per trip_id. The accepted dataset profile records 6,753 trip IDs shared across tenants. Following this literally either blocks valid input or combines different tenants' records.

Require (business_unit, trip_id) for uniqueness, aggregation, joins, evidence, caches and action scope. Tenant filtering after an incorrect join cannot repair the evidence. See [dataset key model](dataset-profile-and-capability-matrix.md).

### 2. High: the mutable DuckDB control file has incompatible process ownership

At lines 371–391 and 426–429, a worker writes /state/control/operations.duckdb while a separate API process reads that file. An exclusive worker lock does not coordinate the API reader. Native embedded DuckDB permits a read/write process or multiple read-only processes; a read-only volume mount does not make concurrent access to the active writer's file valid. See [official DuckDB concurrency documentation](https://duckdb.org/docs/lts/connect/concurrency).

Keep shared mutable jobs, approvals and audit in the existing PostgreSQL control plane. Immutable analytical snapshots are a sound separate use case. Alternatively, define an explicit writer-owned query service or immutable control read model; the plan currently specifies neither.

### 3. High: metric definitions and comparison windows change the golden outcome

At line 656 the baseline becomes the prior equal-length window. Our G1 compares a complete week with the prior four complete weeks. This changes the baseline, delta and potentially the recommendation.

The generic on_time_rate example at line 932 also differs from the governed employee-leg definitions of M04/M05. Bind metrics, eligible populations, caps, units, aggregation, thresholds and comparison windows to M01–M18 and D-039. Do not reset accepted project definitions to unknown, while continuing to label genuine missing organizer semantics. Preserve G1, G2 and the G3 false-anomaly gate.

### 4. High: simulated dispatch does not resolve an incident

The state model at lines 710–736 moves SIMULATED_DISPATCH directly to RESOLVED. A mock ticket or communication proves that an action occurred, not that transport performance recovered.

Separate action completion from operational resolution. Resolution needs an explicit human decision or a defined follow-up evidence check. A persistent incident lifecycle is useful new behavior: our run and approval states do not fully cover acknowledgement, reopening, suppression or outcomes across runs. Keep stable incident identity separate from per-run observations and action IDs.

### 5. Medium: the plan targets a different repository baseline

Lines 170–190 declare the plan canonical and AGENTS.md optional. Phase 1 at line 1215 starts Python/FastAPI/Polars/uv scaffolding. The API contract at line 1028 replaces routes and identity headers.

Python is permitted by the problem statement; it is not a technical defect. The conflict is with the accepted Java implementation under D-028/D-034/D-036 and React under D-035. We already have four agent roles, 18 nodes, seven workers, /api/v1 contracts, Sarvam assistance, async briefs and PostgreSQL snapshots. A replacement requires a migration case, not another scaffold.

Rewrite phases around existing components and preserve API compatibility or explicitly version changes. Translate demo identity controls through our actor resolver instead of treating a caller-supplied role as authorization.

### 6. Medium: one worker and atomic files do not establish latency or recovery guarantees

The protocol at line 410 assigns replay, reporting and action-state work to one exclusively locked worker. It does not specify queue admission limits, priority or a complete claim/retry/idempotency protocol. If commands are processed serially, replay can delay approvals.

Atomic rename prevents partial file visibility. It does not make publication, action state, audit and command completion a single transaction. Specify crash points, deduplication across request retries, bounded admission and approval latency during ingestion. Reuse existing repositories where appropriate; a brief job is not automatically a general action command.

### 7. Medium: dataset intake assumptions are stale

Line 180 expects root-level CSVs; line 275 says only six rows per file were sampled. This project already has full profiling, checksums, tenant join analysis, configured targets and golden fixtures. Official inputs remain under outputs/official dataset/.

Reference the existing field map and capability matrix. Preserve real limitations such as missing GPS, contractual SLA, budget and named-team mapping. Earlier sampling unknowns should not all become new blockers.

### 8. Medium: agent contribution and release gates need an explicit mapping

The orchestration at line 778 describes typed intent routing, tools and narrative selection but does not map them to the existing supervisor, bounded investigator, critic, verifier and briefing roles. Its phases restart completed work and omit the current Sarvam/fallback accounting and G1–G3 baseline.

Preserve bounded planning and investigation, tool budgets, correction limits and node traces. Evaluate provider calls, latency, token use, unsupported claims, tenant leakage, duplicate effects and recovery. Fixed intent routing alone does not demonstrate an improvement in agentic behavior.

## What aligns and what is worth borrowing

| Plan idea | Current fit / useful next step |
|---|---|
| Typed tools, deterministic facts, restricted model output | Strong alignment; preserve existing implementation and adversarial tests |
| Human approval, scoped evidence and audit | Strong alignment; retain full proposal revalidation and idempotency |
| Immutable publication; failed import retains prior usable data | Valuable addition; design publication, request-level snapshot pinning and rollback |
| Source-row provenance and persistent DQ reports | More detailed than current aggregate evidence; add bounded, authorized drill-down |
| Stable incidents across runs | Useful product extension; link observations to an incident and track outcomes |
| Historical-replay and definition labels | Appropriate for this historical dataset; show them near metrics and exports |
| Replay readiness/status UI | Extend existing D-043 runtime capability reporting with publication status |
| Parquet and separate ingestion worker | Valid options; benchmark cold load and concurrent latency before introducing them |

## Corrections to the earlier review

- product_type and shift_type are real source columns, mapped to mode and shift_id. Likewise office maps to site_id and trip_direction to direction. They are not absent data.
- The plan does not establish budget variance as an enabled metric; the earlier criticism was inaccurate.
- D-028/D-034/D-036 establish the current Java baseline, and D-035 selects React. D-012 alone is not the correct reference.
- Different stack choices are migration concerns, not proof of faulty design.
- AnalyticsStore.dataVersion() is a content identifier, not an atomic-publication protocol. WorkflowSnapshotStore persists a decision run, not a published analytical dataset. BriefJobStore is not the whole proposed command plane.
- A standalone incident lifecycle adds capabilities beyond current workflow state.
- Runtime capability reporting already exists; readiness is an extension opportunity.

## Suggested order

1. Correct tenant-qualified keys and control-database process ownership.
2. Bind metrics, windows, actions and tests to existing contracts and G1–G3.
3. Replace scaffold phases with changes to current Java components.
4. Add publication/DQ visibility and incident follow-up where they improve the product.
5. Measure fresh, cached and provider-assisted latency separately before adding runtime or storage complexity.

Evidence: plan.md sections cited above, current code/contracts, the accepted dataset profile, and official DuckDB concurrency documentation. No new analytical or performance measurements were generated for this review.
