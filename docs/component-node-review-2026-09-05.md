# Component and node implementation review — 2026-09-05

The dashboard, four agent roles, seven analytical workers and all 18 workflow nodes have been reviewed. The changes improve bounded concurrency, repeated-request latency, evidence correctness and restart recovery. This remains a local hackathon application with deterministic fallback; these measurements do not establish production capacity or live Sarvam latency.

Decisions: D-045 and D-046 in `hackathon-decision-register.md`. Metric contracts and official files are unchanged.

## Workflow nodes

| Node | Review result / implementation |
|---|---|
| 1 Initialize run | Preserved unique run/trace identity; advanced workflow and prompt metadata to v1.1. |
| 2 Authorize scope | Retained deterministic tenant and permission checks before analytics. Async submission, lookup and rendering also enforce identity scope. |
| 3 Profile dataset | Reused content checksums without loading analytical tables on each submission; full loading occurs on a worker. |
| 4 Build capability matrix | Replaced unbounded caching with a bounded, expiring cache; unsupported analyses remain disabled. |
| 5 Compute metric snapshot | Added bounded single-flight caching keyed by data version and the complete tenant/window/filter query. Errors are not cached. |
| 6 Detect anomalies | Retained governed rules, healthy/no-metric branches and data-quality distinctions; reconciled official data again. |
| 7 Prioritize issue | Retained deterministic severity/priority selection; no model-generated thresholds or scores. |
| 8 Supervisor plan | A model cannot remove the essential full-scope comparisons; dimension values must be allowlisted, strings and bounded. |
| 9 Validate plan | Retained worker/capability allowlists, tenant checks and tool-budget truncation. |
| 10 Run investigations | Replaced per-run executors with a shared bounded executor, bounded queue, rejection handling, deadlines and interruption checks. |
| 11 Merge evidence | Preserves partial/quality caveats, uses registry units for ranking values, and describes delta direction/unit correctly. Peer-query failures are exposed in trace attributes. |
| 12 Evidence critic | Model suggestions may reference existing claims/caveats only; deterministic verification remains authoritative. |
| 13 Verify evidence | Numeric checks use the evidence actually cited by each claim; retained the bounded correction cycle and vendor-blame checks. |
| 14 Compose brief | Model output selects/orders verified claim IDs. Arbitrary model prose cannot enter the leadership narrative. All caveats survive model selection. |
| 15 Action policy gate | Missing or failed verification routes to report-only. Retained action allowlist, tenant, evidence-version and expiry checks. |
| 16 Approval interrupt | Saves rich run snapshots in PostgreSQL, closes the trace at the pause and freezes completed-brief elapsed time. |
| 17 Revalidate/execute | Revalidator requires the entire executing proposal to match the approved proposal, including approved edits. Freshness, authorization and idempotency remain mandatory. |
| 18 Append audit | Preserved append-only audit; aligned provider-attempt and token accounting with API reporting. Durable snapshots use checkpoint-version guards. |

## Agents and investigation components

The supervisor, investigator, critic and briefing agent retain deterministic implementations. Sarvam is optional assistance through the existing `LanguageModelPort`; it cannot calculate metrics, grant authorization, approve proposals or execute actions.

The investigation loop was checked at each stage: select a registered tool; validate scoped arguments; execute within the shared budget/deadline; validate returned evidence and decide whether another bounded step is warranted. Follow-up filters remain evidence-derived. Returned worker identity, current window and metric/ranking data versions are checked before merging. Tool spans now carry actual durations.

| Worker | Retained contract and review outcome |
|---|---|
| Vendor | Full-scope comparison precedes attribution; qualified vendor dispersion governs escalation. |
| Site / shift / direction | Deterministic narrowing retained; model suggestions cannot replace the evidence-derived follow-up scope. |
| Delay reason | Governed distributions remain distinct from trip-rate measurements. |
| Cost / billing | Unsupported monetary conclusions stay caveated; no invented savings. |
| Feedback | Coverage and quality warnings survive evidence merging. |
| Tracking / safety | Capability gates and the G3 data-regime caveat remain intact; no automatic severe-alert escalation. |
| No-show / roster | Existing leg-level metric semantics and tenant-qualified joins retained. |

## UI components

| Component | Change / retained behavior |
|---|---|
| App shell | Responsive slate/teal workspace, consistent navigation, readable hierarchy and busy-state controls. |
| Brief loading | Async polling with backoff and cancellation; stale tenant/date requests cannot replace current results. |
| KPI cards | Human-readable metric labels, governed values, correct target status and explicit units/populations. |
| Comparison charts | Lightweight memoized bars; shared zero-based scale, current/baseline readings, evidence drill-down and up to eight real segments. Numerator shares are excluded from rate comparisons. |
| Investigation | Retained branch status, capability gaps, claim citations and the governed evidence table. |
| Approval | Retained consequence preview and approval/rejection flow; late responses cannot overwrite another tenant's screen. |
| Audit | Ignores stale responses after navigation or identity changes. |
| Trust | Provider attempts, fallback use, tokens and configured cost estimates; unknown cost remains unknown. |
| Ask / evidence drawers | Focus containment, Escape, stacked-dialog handling and focus restoration. Questions cannot display stale results after a new brief. |
| Leadership sharing | Working clipboard copy and plain-text download including provenance. |
| Shared styles | Responsive layouts, visible keyboard focus, reduced-motion and print rules. |

## Performance and recovery

- Metric cache: 2,048 entries / ten-minute TTL; capability cache: 128 entries / ten-minute TTL. Concurrent identical misses share computation.
- Async queue: 256 active-job admission limit; two workers by default (configurable 1–16). PostgreSQL claims use `FOR UPDATE SKIP LOCKED`; replicas share jobs and completed run snapshots.
- Brief reuse: ten minutes, scoped to actor, roles, tenant, persona, date, data/metric/workflow/prompt/model versions and deployment cache namespace. It reuses a run and its current approval state rather than creating duplicate approval requests on each dashboard visit.
- Optional precomputation warms the configured historical date and demo transport-manager identity for configured tenants. It does not silently advance beyond the supplied dataset.
- Abandoned jobs fail visibly and are not automatically replayed. A crash during an active workflow is not arbitrary-node continuation. Approval/checkpoint/snapshot writes are not one distributed transaction; missing state fails closed.
- In-memory mode is for one local process. PostgreSQL is required for shared control-plane recovery. DuckDB and metric caches remain per process; additional replicas need the same immutable dataset and configuration. The synchronous compatibility endpoints do not use the async admission queue.

### Measured local sample

Official data version `data-8ed5b4eae158`; `pinnacle-Slc`, 2026-06-08; model `none`; eight requests per scenario. The server was already warm. No pre-change baseline, cold-start benchmark, sustained soak test, memory-capacity measurement or live-provider measurement is claimed.

| Mode | Concurrency | p50 ms | p95 ms | Requests/sec | Errors |
|---|---:|---:|---:|---:|---:|
| Synchronous compute, warm metric cache | 1 | 25.05 | 41.66 | 33.15 | 0 |
| Synchronous compute, warm metric cache | 4 | 44.98 | 63.14 | 75.82 | 0 |
| Completed async-run reuse | 1 | 2.30 | 2.88 | 422.58 | 0 |
| Completed async-run reuse | 4 | 3.62 | 4.87 | 912.15 | 0 |

Repeat with `python3 scripts/performance/benchmark.py --requests 20 --concurrency 1 4 8` against a running official-data backend. Restart first to measure the initial request separately. Reuse measurements are cache hits, not fresh investigations.

## Validation

- 136 default backend tests passed, including malformed/provider-timeout handling, bounded-cache isolation, job reuse, rich-snapshot round-trip and hostile model-output regression.
- Six dedicated PostgreSQL adapter tests passed against an isolated disposable database: checkpoint/approval/idempotency/audit, atomic job claims and snapshot-version guards.
- A real PostgreSQL-profile application was started, an async brief completed, the application was stopped and restarted, and the same job, evidence and frozen latency were successfully read back.
- Eight frontend tests passed, including chart-to-evidence navigation and keyboard focus restoration. Production build checked. No browser screenshot/visual audit was performed in this pass.
- Immutable dataset checksums and official DuckDB reconciliation passed. Fixture HTTP smoke and official-data HTTP approval/adversarial scorecard passed. G1, G2, G3, security and audit gates passed with zero recorded zero-tolerance violations.
- The release wrapper initially encountered the already-running official-data benchmark server during its fixture stage. That server was stopped and the remaining fixture, official-data and artifact stages were rerun successfully.
- Run default tests without `-Ppostgres`; run the dedicated PostgreSQL test with that Maven profile. Enabling PostgreSQL dependencies for the full default suite without its Spring runtime configuration attempts to auto-configure an unconfigured datasource.

## Sarvam and deployment setup

Export environment variables explicitly; `.env.example` documents names but is not automatically sourced by Maven.

```sh
export LANGUAGE_MODEL=sarvam
export SARVAM_API_KEY='your-key'
export SARVAM_MODEL=sarvam-105b
export SARVAM_MAX_CONCURRENT=2
export BRIEF_WORKERS=2
export BRIEF_CACHE_NAMESPACE=deployment-1
./mvnw -pl backend spring-boot:run
```

The key stays on the server. The adapter uses the documented HTTPS chat endpoint, `api-subscription-key`, structured JSON, output-token limits, request timeout, bounded concurrency and a 30-second failure cooldown. Missing credentials, provider failures and unusable output fall back deterministically. Local HTTP tests cover the wire contract; a live Sarvam request has **not** been verified because no key was supplied. See [Sarvam chat API](https://docs.sarvam.ai/api-reference/chat/chat-completions) and [authentication](https://docs.sarvam.ai/api-reference/authentication).

Optional settings: `MODEL_INPUT_COST_PER_MILLION`, `MODEL_OUTPUT_COST_PER_MILLION`, `MODEL_COST_CURRENCY`; populate from your actual provider agreement. `BRIEF_PRECOMPUTE_ENABLED=true`, `BRIEF_PRECOMPUTE_AS_OF=2026-06-08` and `BRIEF_PRECOMPUTE_TENANTS=pinnacle-Slc` enable historical precomputation. Change `BRIEF_CACHE_NAMESPACE` whenever answer/policy-affecting deployment settings change; keep it and configuration consistent across replicas.

For persistent mode, configure `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, then run:

```sh
./mvnw -Ppostgres -pl backend spring-boot:run -Dspring-boot.run.profiles=postgres
```

Flyway applies V3 alongside existing migrations. No public deployment or external vendor communication was performed. Action adapters remain mock adapters; production scaling still requires authenticated ingress, operational monitoring, retention/backups, live-provider benchmarking and representative sustained load tests.
