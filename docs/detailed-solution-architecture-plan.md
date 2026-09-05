# Mobility Decision Copilot: Detailed Solution Architecture

> Branch scope: implementation descriptions refer to Java-branch-2. This Java-branch copy transfers documentation only; see [branch scope](architecture-branch-scope.md).

Updated 2026-09-05 under D-048. This describes the implemented Java runtime and separately identifies proposed extensions. [HLD](high-level-design.md) owns the deployment, API and operational-limit overview; [dataset contracts](dataset-profile-and-capability-matrix.md) own analytical semantics.

## 1. Runtime and boundaries

Java 21/Spring Boot composes a DeterministicWorkflowEngine behind WorkflowEngine/ResumableWorkflowEngine. Four logical roles share typed ports, not independently deployed agent services. LangGraph4j is gated and not the active execution adapter. Spring AI remains optional; SarvamLanguageModel implements LanguageModelPort directly.

The browser's morning brief enters AsyncBriefService through POST /api/v1/brief-jobs. The dispatcher invokes WorkflowDecisionRunGateway on a bounded background worker. Synchronous workflows/questions still enter the same governed engine. A job's COMPLETE state means its workflow result is available, including when that workflow is AWAITING_APPROVAL.

Deterministic code owns authorization, tenant scope, metric calculations, anomaly selection, verification, policy and state transitions. Only registered analytical worker tools are available to investigation. No model-generated SQL, raw operational rows, shell tools or external action tools enter the agent surface.

## 2. Four agent roles

| Role | Input/output and bounded authority | Implemented hardening |
|---|---|---|
| SupervisorAgent | Issue, persona, capabilities and budget → InvestigationPlan | Essential full-scope comparisons survive incomplete model plans; filters are allowlisted, typed and bounded |
| InvestigationAgent | One scoped task and typed WorkerEvidenceDto tools → InvestigationResult | Shared bounded execution; deadlines, interruption, task/window/version validation; follow-up narrowing stays evidence-derived |
| EvidenceCriticAgent | Existing claims, bundle and deterministic verifier findings → Critique | Model overclaim/caveat suggestions may reference existing material only; no new facts or tools |
| BriefingActionAgent | Verified claims and deterministic action rules → BriefingOutput / ActionProposal | Model can select/order verified claim IDs; exact claim text and mandatory caveats are rendered deterministically |

All roles have deterministic fallback. ModelAssist records attempts/usage and allows one parse retry. Successful JSON parsing alone is not evidence validation; each role still constrains semantic output.

Sarvam receives typed aggregate payloads and versioned prompts. The key is server-side. Missing configuration, timeout, provider failure, cooldown or exhausted concurrency falls back. Input/output token accounting and configured cost estimates are exposed; no live-key result or hard financial spending cap is claimed.

## 3. Eighteen top-level nodes

Node names below match WorkflowNode. START/END and the four reusable investigation stages are not counted as top-level nodes.

| # | Node | Owner / responsibility | Route or invariant |
|---:|---|---|---|
| 1 | INITIALIZE_RUN | Deterministic run/request/version/budget setup | Typed identity and unique run/trace refs |
| 2 | AUTHORIZE_SCOPE | Deterministic tenant and permission checks | Denied requests fail before analytics |
| 3 | PROFILE_DATASET | Load analytics capabilities/profile | Quality and unavailable sources remain visible |
| 4 | BUILD_CAPABILITY_MATRIX | Determine supported analyses | Disabled capabilities cannot become tasks |
| 5 | COMPUTE_METRIC_SNAPSHOT | Governed detection snapshot and data version | Cached metrics retain full tenant/window/filter semantics |
| 6 | DETECT_ANOMALIES | Material candidates and data-regime notes | Healthy brief or no-metrics failure; otherwise prioritize |
| 7 | PRIORITIZE_ISSUE | Deterministic selected issue | No model-generated severity/threshold |
| 8 | SUPERVISOR_PLAN | A1 bounded plan | Deterministic fallback and mandatory comparisons |
| 9 | VALIDATE_PLAN | Registered workers, tenant, capabilities and budget | Remove invalid tasks; empty valid plan fails |
| 10 | RUN_INVESTIGATIONS | A2 task fan-out / typed merge inputs | Shared executor; preserve successful/partial branches |
| 11 | MERGE_EVIDENCE | Deduplicate, link and version evidence | Registry units, provenance and partial/quality caveats retained |
| 12 | EVIDENCE_CRITIC | A3 structured review | Existing claim/caveat references only |
| 13 | VERIFY_EVIDENCE | Deterministic cited-evidence and claim checks | At most one claim-removal/reverification correction; no new investigation loop |
| 14 | COMPOSE_DECISION_BRIEF | A4 dual output and deterministic action proposal | Verified-ID narrative selection; fixed text fallback |
| 15 | ACTION_POLICY_GATE | Action type, scope, evidence, confidence and expiry | Missing/failed verification → report-only; eligible → approval |
| 16 | APPROVAL_INTERRUPT | Create pending approval and checkpoint | Return to caller; trace closes; rich snapshot persisted at outcome |
| 17 | REVALIDATE_AND_EXECUTE | Resume with approval/edit/rejection | Fresh evidence, authority, expiry and full proposal match before idempotent mock effect |
| 18 | APPEND_AUDIT_EVENT | Audit summary, checkpoint and terminal outcome | Business audit remains separate from diagnostic telemetry |

~~~mermaid
flowchart TD
    START["Request / claimed brief job"] --> INIT["1 Initialize"]
    INIT --> AUTH["2 Authorize"]
    AUTH --> PROF["3 Profile"]
    PROF --> CAP["4 Capabilities"]
    CAP --> SNAP["5 Metric snapshot"]
    SNAP --> DET["6 Detect"]
    DET -->|"healthy"| HEALTHY["Healthy brief"]
    DET -->|"no metrics"| FAIL["Failed result"]
    DET -->|"material"| PRI["7 Prioritize"]
    PRI --> PLAN["8 Supervisor"]
    PLAN --> VALID["9 Validate plan"]
    VALID --> INV["10 Investigations"]
    INV --> MERGE["11 Merge"]
    MERGE --> CRITIC["12 Critic"]
    CRITIC --> VERIFY["13 Verify"]
    VERIFY -->|"one correction"| CORRECT["Drop unsupported claims and reverify"]
    VERIFY --> COMPOSE["14 Compose"]
    CORRECT --> COMPOSE
    COMPOSE --> POLICY["15 Policy gate"]
    POLICY -->|"eligible"| PAUSE["16 Approval pause"]
    POLICY -->|"report only"| AUDIT["18 Audit / terminal outcome"]
    PAUSE -->|"human decision / resume"| RESUME["17 Revalidate and execute"]
    RESUME --> AUDIT
    HEALTHY --> AUDIT
    FAIL --> AUDIT
~~~

The correction branch does not return to the supervisor or consume fresh analytical tool calls. Rejection is handled on resume without executing a mock action. Edits must pass validation; they do not bypass node 17.

## 4. Reusable investigation loop

~~~mermaid
flowchart LR
    TASK["Isolated task"] --> CHOOSE["choose_analysis"]
    CHOOSE --> EXEC["execute_analysis"]
    EXEC --> CHECK["validate_tool_result"]
    CHECK --> PROGRESS{"progress_gate"}
    PROGRESS -->|"evidence needed and budget available"| CHOOSE
    PROGRESS -->|"complete / partial / exhausted"| RESULT["InvestigationResult"]
~~~

InvestigationAgent uses four shared threads and a queue of 64 tasks. Rejection and deadline exhaustion return qualified failure/partial results. Work is cancelled on exhaustion; interruption checks prevent late task results from silently becoming completed evidence. Tasks share run-level counters for tool use, while task evidence remains isolated until merge.

WorkerToolRegistry supplies seven typed GatewayWorkerTool adapters; the implementation does not require seven separate agent classes.

| Worker ID | Governed domain |
|---|---|
| vendor | Vendor dispersion and qualified comparison |
| site_shift_direction | Site, shift and direction concentration |
| delay_reason | Delay-reason distributions |
| cost_billing | M09/M10 cost evidence and unsupported-km caveats |
| feedback | Ratings and coverage warnings |
| tracking_safety_alerts | Alert/tracking metrics, including G3 regime-change exclusion |
| noshow_roster | Employee-leg no-show and roster-derived measures |

Source names map to the governed dimensions: product_type → mode, office → site_id, shift_type → shift_id, trip_direction → direction. Use (business_unit, trip_id) for all trip joins and aggregates. Qualification and formulas come from M01–M18 v1.1; the prior-four-complete-week baseline is not replaced with an equal-length week.

## 5. Evidence and state

| Type / port | Purpose |
|---|---|
| RunContext | Actor/persona/mode, trace, model and data/workflow/prompt versions |
| WorkflowState | Run/tenant/date, current step, tasks and bounded execution counters |
| WorkflowRun | Context, state, detection, plan, investigation results, evidence, critique, verification, briefing, policy, approval, receipt, transitions and usage |
| WorkflowRun.Snapshot | Serializable rich completed/paused run without raw CSV rows, connections or executors |
| WorkflowCheckpointStore | Versioned workflow state with optimistic checks |
| WorkflowSnapshotStore | Rich snapshot save/find; PostgreSQL guards against older checkpoint-version overwrites |
| EvidencePackage / EvidenceBundle | Typed items, claims, gaps, caveats and evidence version |
| ModelUsage | Role/model/prompt metadata, provider attempts, tokens, latency and fallback information |
| TransitionEvent | Node/subnode outcome, duration and diagnostic attributes |

Numeric claims are checked against evidence IDs cited by that claim. Ranking values use metric-registry units; numerator-share evidence is distinct from metric values. Partial branch warnings and capability gaps survive final rendering. Peer access remains permission-controlled.

Workflow/prompt metadata are workflow-v1.1 and prompts-v1.1. Runtime prompt files remain in the prompts/v1 resource family. A deploy changing answer/policy-affecting settings must use a new BRIEF_CACHE_NAMESPACE; replicas sharing a namespace require consistent data and configuration.

## 6. Caching, jobs and recovery

DuckDbMetricService uses BoundedMetricCache with 2,048 entries and ten-minute TTL; capability caching uses 128 entries. Complete query scope and data/metric versions participate in metric keys. Identical in-flight misses share computation; failed results do not enter the cache.

AsyncBriefService authorizes before reuse and uses a digest of actor, roles, tenant, persona, date, data/metric/workflow/prompt/model versions and namespace. Job TTL is ten minutes; two brief workers are the default, configurable from 1 to 16. Queue capacity is bounded at 256. Optional precomputation uses a configured historical date, tenant list and demo transport-manager identity; it is off by default.

In-memory mode is a local fallback. PostgreSQL shares jobs and rich run snapshots with the existing approval/checkpoint/action/audit repositories through migration V3. Atomic job claims prevent two replicas from claiming the same queued job. An abandoned running job fails visibly; it is not replayed blindly because a pending approval may already exist.

Completed/paused snapshots support run retrieval after restart and supply evidence for approval resume. Arbitrary-node continuation is not implemented. Snapshot, checkpoint, approval and audit operations do not form one all-or-nothing transaction. A missing/mismatched prerequisite must fail closed. A job's successful completion is not approval or action completion.

## 7. Approval and outcomes

Policy requires evidence and passing verification. Approval UI displays the proposal's consequence, scope and evidence version. On approve/edit, deterministic revalidation checks fresh analytical evidence and the entire approved proposal. Idempotency and audit protect mock effects. Rejection, expiry and stale evidence produce explicit states.

Action adapters remain mocks: watchlist, investigation ticket, vendor escalation draft or communication draft. No vendor message is sent. EXECUTED describes the adapter effect; it does not mean a transport incident was resolved.

A proposed incident extension will maintain stable incident identity across run observations and separate acknowledgement/reopen/resolution from action state. It needs follow-up evidence or explicit human closure and is not part of today's implemented state model.

## 8. UI and telemetry

The React shell provides Morning brief, Investigation, Approval, Audit and Trust views plus Ask and evidence drawers. Comparison charts use governed current/baseline evidence on a shared zero-based scale; rate comparisons exclude numerator-share items. Tenant/date changes cancel polling and invalidate stale UI responses. Dialogs support focus containment, Escape and focus return; reduced motion and responsive layouts are included. Clipboard/text download includes provenance.

Tracing attaches parallel tool spans to the correct parent, records actual tool durations and closes roots at pause/terminal outcomes. Provider attempts, fallback evaluations, tokens and configured cost estimates are distinct. Langfuse export is optional; the business ledger remains authoritative. Cache-hit response latency is not fresh investigation latency; resumed run lifetime may include human approval wait.

## 9. Proposed extensions and activation gates

- Analytical publication: stage/validate/finalize immutable datasets, pin request versions, retain a last-good publication and test failed refresh rollback. A data checksum or decision-run snapshot is not this protocol.
- Source-row DQ provenance: extend current ingestion/evidence with bounded, tenant-filtered and redacted drill-down.
- Incident lifecycle: link repeat observations across runs and measure follow-up outcomes.
- Separate ingestion/Parquet: consider only with representative cold-load/concurrency evidence. PostgreSQL owns shared mutable control state; native DuckDB files are process-local or immutable published read models.
- LangGraph4j: requires a routing/fan-out/serialization/pause-resume/trace spike before replacing the active state-machine adapter.
- Knowledge/RAG: disabled without decision-relevant documents, at least five document-dependent golden questions, enforceable tenant/version/citation metadata and retrieval evaluation within budget.

## 10. Verification and ownership

Keep G1–G3, official checksums, M01–M18 fixtures, corrupted inputs, tenant/security checks, approval/idempotency tests and trajectory tests as the regression baseline. Add provider wire/failure tests, cache isolation, async identity reuse, PostgreSQL claims/snapshot guards, application restart recovery and UI focus/cancellation coverage.

Recorded validation and benchmark limits are in [component/node review](component-node-review-2026-09-05.md). Current source paths are in [project structure](project-structure.md); active endpoints and operational configuration are in the [HLD](high-level-design.md). Changes still follow the Integration Owner and worktree rules in D-037/D-040; no new parallel implementation is implied by this document.
