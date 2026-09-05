# Four-agent backend implementation review

Reviewed against `docs/agents-guide.md`, `docs/requirement.md`, `docs/architecture.md`, the problem-statement walkthrough and DS-01–DS-20. Main was pulled to `92b0a5e` before implementation and fast-forwarded to `925fa0c` to include the subsequent UI changes before publication. Existing local agent-guide changes were preserved. See [the setup guide](../docs/setup-guide.md) for local run commands.

The four report agents, seven investigation workers and all 18 metric contracts now have an integrated Java backend. The latest validation passed **50 tests, zero failures/errors/skips**, including all 20 dataset scenarios. This completes the governed investigation/reporting path; it does **not** make the entire production application ready. Authenticated serving, durable approval/action execution, production telemetry and sustained capacity qualification remain separate delivery work.

## Definition versus implementation

| Role and required behavior | Finding on pulled main | Implemented behavior and validation |
|---|---|---|
| Supervisor: scope an issue, preserve required investigations, honor capabilities and budget | Partial planner and conflicting fallback wiring | One authoritative planner; deterministic fallback; allowlisted optional model suggestions; mandatory current/prior-four-complete-week comparisons; tenant/version validation; insufficient mandatory-comparison budgets fail explicitly. Contract tests cover mandatory branches and model narrowing. |
| Investigator: gather scoped evidence through registered tools, handle partial work | Worker-specific CSV access, incomplete request handling and duplicate implementations | All seven workers share governed SQL; execute current and baseline requests; validate DAGs before tools; bound process-wide execution and shared request budget; preserve completed evidence and explicit gaps. Tests cover dependencies, cycles, failed branches, wrong periods/tenants, queue saturation and registry collisions. |
| Evidence critic: verify arithmetic, provenance and claims before narrative | Conflicting verifier/critic implementations; reference checks could accept unsupported wording | One deterministic verifier plus optional bounded semantic rejection. Canonical direct facts and scope-matched two-period differences; metric-family version checks; ratio arithmetic; missing/foreign evidence rejection; causal/vendor-blame statements rejected. A model cannot create verified facts. Tests include altered values/deltas and semantic rejection. |
| Briefing/action: produce consistent audience views and controlled drafts | Partial rendering and draft factory existed | Both views retain the same verified facts and caveats. Rejected evidence is suppressed. Internal action drafts require explicit trusted policy and eligible verified claims/targets; no action is executed. Tests cover rejected briefs, scope checks and policy drafts. |

`AgentWorkflowService.investigate(context, metricRequest)` is the typed entry point for a governed metric investigation. `execute(context, plan)` supports reviewed multi-domain compositions such as DS-20. The context must originate from a trusted server boundary. The default state-machine start executes the reporting path and records a completed/partial checkpoint; it no longer pretends to execute all 18 declared workflow nodes. Resume explicitly requires the separate durable action service.

The implementation deliberately reports descriptive comparisons. Automatic anomaly selection/prioritization, calibrated confidence, causal attribution, autonomous follow-up research and production approval replay are not established by these tests. Existing definitions for those wider workflow capabilities remain requirements, not completed runtime claims.

## Governed analytics and dynamic scenarios

M01–M18 use a versioned normalized DuckDB snapshot and parameterized, registered SQL. Supported dimensions are business scope plus the allowlisted vendor, site, shift, direction, mode, vehicle and fuel filters. Tenant scope is required independently of filters. Dynamic requests compose these contracts and explicit windows/statistics; arbitrary SQL, new metrics and unsupported business definitions are rejected.

Trip joins and uniqueness use `(business_unit, trip_id)`. Child facts retain their approved eligibility/deduplication rules. Missing populations return unavailable/partial evidence with reasons. M03 expands explicit reason variants; M02 supports mean/P90; M12 supports driver/safety variants. Q2 keeps M10 unavailable for the two tenants with unreliable billed-distance coverage. Billing windows include complete source cycles without inventing daily cost allocations.

The additive daily trip rollup serves M01/M17 window/group queries where its dimensions apply; vehicle-level requests use normalized facts. Ratios divide summed numerators by summed denominators. M08 uses eligible capped occupied seats divided by eligible capacity, as explicitly recorded in D-060. Exact medians and percentiles run over eligible facts; they are never reconstructed by averaging daily percentiles.

Facts are ordered by tenant/date to support DuckDB zone-map pruning. The composite trip index enforces uniqueness and supports selective lookup; it is not presented as an accelerator for arbitrary aggregates. This distinction follows [DuckDB indexing guidance](https://duckdb.org/docs/current/guides/performance/indexing). A PostgreSQL migration adds tenant/run/time indexes to checkpoint/audit lookups and run/status/time indexes to action/approval lookups. That migration has not been executed against PostgreSQL in this validation.

## Scale evidence and limits

The supplied files contain **615,546 trip rows and 1,637,906 employee-leg rows**, independently counted by DuckDB. These are row counts, not unique users. The snapshot eliminates repeated source CSV parsing from the metric query path.

The final local run used Java 21.0.10 on arm64, four client threads, two DuckDB threads and a configured 512 MB DuckDB memory limit (reported as 488.2 MiB). Fifteen M01 queries across five tenants and three months completed in **33 ms** as one concurrent batch and matched their cached results. Reopening/profiling the existing snapshot took **914 ms**. These are single-run warm-database observations, not p95/p99 latency, cold-import performance, total process-memory limits or throughput guarantees. The test also verifies an aggregate query plan references `trip_daily` without `READ_CSV`.

Operational limits are configurable under `mobility.analytics`: database path, memory limit, SQL threads, connection count, query timeout, cache entries and maximum returned groups. Defaults are 512 MB, two SQL threads, four connections, 30-second query timeout, 256 cache entries and 100 groups. The investigator separately uses a bounded shared executor; optional model calls have bounded admission, input size and deadlines. Query timeouts and admission limits protect a single process; they do not provide distributed fairness.

To move from this dataset to millions of users' data, retain the metric contracts while introducing partitioned immutable snapshots or warehouse tables, incremental tenant/date rollup refresh and measured partition pruning. For multiple application instances, publish read-only snapshots per serving instance or use a shared analytical service; do not have replicas write the same embedded database file. DuckDB's [concurrency model](https://duckdb.org/docs/current/connect/concurrency) constrains that deployment choice. Keep transactional approvals/audits in PostgreSQL with migrations and tenant authorization.

Before a production capacity claim, measure cold ingestion, mixed-metric queries (especially exact quantiles and billing), high-cardinality grouping, sustained concurrency, memory/spill growth, cancellation and overload recovery at representative tenant skew. Derive instance counts and admission limits from those measurements and an agreed latency target. No fabricated million-user dataset or unapproved business scenario was used in this work.

## Dataset corrections

Source version: `576dca7842d8d7d0f2b37a87c1096bc51c388b22c23d46e792247c24586f9acc`.

- vanta-Aus has Cedar Ridge Office (69,801 trips) and Santa Clara Office (398 trips). Reports use observed office concentration and group populations, superseding the earlier single-office assumption.
- vanta-Sea May M09 is 1,390.34 at two-decimal precision; 1,390 is the whole-currency display value. An independent raw-source DuckDB aggregation reconciles the normalized service result.
- Pinnacle-Slc June has no eligible severe-alert resolution population. DS-16 checks unavailable there and uses catalyst-Bay June for an eligible result.
- Q2 is a coverage restriction. Earlier wording that every billed kilometre was zero was too strong and has been corrected without relaxing the governed capability gate.

Official CSVs were not modified. Material decisions are recorded as D-057–D-061 in the current consolidated architecture decision section, with corresponding requirement/scenario updates.

## Tests and reproducibility

`OfficialDatasetScenariosTest` contains the 20 actual-data scenarios plus source reconciliation and concurrent-query checks. `AgentContractRegressionTest` covers 13 isolated failure/control cases. Other tests cover service ingestion, arithmetic/claim verification, application wiring, existing anomaly contracts and action/briefing behavior. Synthetic fixtures test controls only; their numbers are not business goldens. Passing these checks is not a claim that every metric/filter combination has an independent oracle or that all production gates have passed.

Run with Java 21 and the actual supplied files:

```sh
./mvnw -pl backend test '-DofficialDataset=/absolute/path/to/outputs/MoveInSync - Anonymised Trip-Log Dataset'
```

Without `officialDataset`, the official-data class is explicitly skipped; a normal unit-test run therefore cannot substitute for the recorded dataset run. The integration test writes a derived database under `/private/tmp` and leaves source files untouched. No external language-model or operational action calls are required.

## Role skills and key implementation paths

All four skills pass the skill-creator structural validator. They guide development/evaluation and do not grant runtime permissions.

- [Supervisor skill](../.agents/skills/mobility-supervisor/SKILL.md)
- [Investigator skill](../.agents/skills/mobility-investigator/SKILL.md)
- [Evidence critic skill](../.agents/skills/mobility-evidence-critic/SKILL.md)
- [Briefing/action skill](../.agents/skills/mobility-briefing-action/SKILL.md)
- [Typed orchestration](../backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/application/AgentWorkflowService.java)
- [Governed metric service](../backend/src/main/java/com/moveinsync/mobilitycopilot/metrics/adapter/duckdb/OfficialDuckDbGovernedMetricService.java)
- [Normalized facts and rollup schema](../backend/src/main/resources/sql/schema/02_official_analytics.sql)
- [PostgreSQL lookup indexes](../backend/src/main/resources/db/migration/V2__tenant_query_indexes.sql)
- [Actual-data tests](../backend/src/test/java/com/moveinsync/mobilitycopilot/metrics/adapter/duckdb/OfficialDatasetScenariosTest.java)
