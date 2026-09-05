# Mobility Decision Copilot — component responsibility map

This map assigns responsibilities without exposing reference source files or requiring a particular class layout. The team can choose its internal structure after agreeing the shared records.

## 8. Every main architecture component

| Component | What it does | What it hands to others | Suggested work package |
|---|---|---|---|
| Dashboard | Shows briefs, evidence, questions, approval, audit and trust readings | Scoped user requests and decisions | 5 |
| API boundary | Validates requests and connects screens to services | Consistent responses and safe errors | 4 |
| Identity and access | Determines who may see or approve what | Authorized business-unit and role scope | 3 |
| Request queue | Accepts bounded background work and reports progress | Job identifier, status and result reference | 4 |
| Scheduler | Requests precomputation for configured historical dates and tenants | The same scoped work requests used by the UI | 4 |
| Data loader and normalizer | Reads untouched files and handles approved formatting differences | Consistent data with provenance | 1 |
| Data-quality profiler | Finds missing, invalid, duplicate and unmatched records | Quality report and exclusions | 1 |
| Capability checker | Matches data availability to analysis requirements | Supported/limited/unavailable flags | 1 |
| Metric registry | Defines each approved measurement and its denominator, unit and exclusions | Versioned definitions | 1, agreed with integration owner |
| Analytical store and query service | Executes the governed calculations | Checked metric and contribution evidence | 1 |
| Metric cache | Reuses recent calculations for identical authorized scope and versions | The same result with less repeated work | 1 |
| Detector and prioritizer | Applies approved change/materiality/priority rules | Selected issue and its context | 1 |
| Workflow controller | Coordinates the 18 steps, errors and budgets | A traceable run and terminal/pending status | 2 |
| AI provider adapter | Sends bounded aggregate prompts to optional Sarvam assistance | Structured suggestions and usage records | 2 |
| Evidence merger and verifier | Combines results and enforces claim rules | Verified claims plus limitations | 2 |
| Report renderer | Presents checked facts for two audiences | Briefs, answers and shareable text | 4 |
| Action policy and approval | Decides whether a proposal can be shown for a human decision | Pending/approved/edited/rejected decision | 3 |
| Revalidation and action executor | Rechecks approved details and prevents duplicate simulated effects | Execution receipt or reason nothing happened | 3 |
| Durable control store | Saves jobs, progress, pending decisions, evidence snapshots and receipts | Restart-readable control records | 3; queue behavior owned by 4 |
| Business audit ledger | Records consequential business events | Who decided what, with which evidence and outcome | 3 |
| Observability and evaluation | Measures durations, tool/model use and failures; checks release behavior | Diagnostics and acceptance evidence | 6 |

Recommended technology choices can remain Java/Spring Boot, React/TypeScript, DuckDB analytics, PostgreSQL shared control state, optional Sarvam and OpenTelemetry/Langfuse. These choices do not change the responsibilities above.

## 9. What the components must exchange

Agree these records before building screens or independent components. Each record needs required/optional fields and error behavior. Use business names, then choose implementation names together.

| Record | Minimum meaning |
|---|---|
| Request | Who asked, business unit, role, as-of date, request purpose and allowed filters |
| Job status | Job ID, queued/running/complete/failed, timestamps, result reference and safe error |
| Run | Run ID, scope, current step, versions, work allowance, errors and outcome |
| Metric evidence | Metric ID/version, business unit, period, filters, value/unit, population, numerator/denominator when applicable, source and quality |
| Investigation plan | Selected issue, allowed tasks, scope, required evidence and stop conditions |
| Task result | Worker, complete/partial/failed status, evidence, findings and limitations |
| Claim | Claim ID, exact text, kind and supporting evidence IDs |
| Brief | Checked claims, operations/leadership presentations, caveats and optional proposal |
| Proposal | Action ID, type, run, tenant scope, exact proposed details, evidence version and expiry |
| Human decision | Proposal/approval identity, approver, approve/edit/reject, time and approved details |
| Receipt | Action identity, duplicate-prevention key, simulated outcome, time and audit reference |
| Audit event | Tenant/run/action references, actor, event type, time and outcome |

A data version identifies an analytical dataset. A run ID identifies one analysis attempt. An action ID identifies one proposal/effect. Keep them distinct.


## Ownership and handoff

Use the [team work packages](team-handbook/team-work-packages.md) for assignments, dependencies and acceptance criteria. Each person owns a capability with agreed inputs/outputs; the integration owner controls shared-contract changes. Runtime agent count does not determine developer count.
