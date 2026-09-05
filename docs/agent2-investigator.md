# Agent 2 — Investigator: Plan, Design, Requirements, Debug Guide

Version 1.0 · 2026-09-05 · Owner: WS2

---

## 1. What Agent 2 Does (Plain English)

Agent 2 is the **Investigator**. It receives one approved investigation task at a time from the Supervisor's plan (Agent 1), runs governed analytical tools against MoveInSync's trip data, and returns structured evidence with clear completeness/failure status.

It does NOT calculate KPIs itself — it calls WS1 tools (DuckDB-backed metric workers). It does NOT approve actions. It CANNOT generate arbitrary SQL. It stops when its work budget runs out.

---

## 2. AIDLC Phases

### Phase A — Alignment

**Business goal:** Answer one investigation question per task (e.g. "Did vendor X worsen, or did all vendors worsen?") using only approved data workers, within bounded resource limits.

**Persona served:** Transport Manager, via node 10 of the 18-node workflow.

**Success looks like:** For golden case G1 — a site/shift concentration finding and correct rejection of unsupported single-vendor blame is produced. For G2 — explicit coverage caveat when data is limited. For G3 — alert-regime change detected and NOT escalated as operational incident.

**Non-goals:**
- Agent 2 does not generate or validate SQL.
- Agent 2 does not call LLM for facts — only for choosing which approved tool to run next.
- Agent 2 does not authorize tenants, calculate thresholds, or take actions.

---

### Phase I — Investigation

#### Inputs (what Agent 2 receives)

| Input | Type | Source |
|---|---|---|
| `RunContext` | `RunContext` | WS3 (authenticated, authorized) |
| `InvestigationPlan` | `InvestigationPlan` | Agent 1 (Supervisor) |
| One `InvestigationTask` per worker | `InvestigationTask` | From the plan's task list |

`RunContext` carries: tenant, actor, asOfDate, budget (`maxToolCalls=12`, `maxDepth=4`, `investigationTimeout=10s`, `maxParallelTasks=4`), data/metric versions.

#### Outputs (what Agent 2 produces)

`InvestigationResult`:
- `evidence` — list of `MetricEvidence` from WS1 workers
- `completedTasks` — only tasks actually executed (not merely planned)
- `pendingTasks` — tasks not reached due to budget/timeout
- `warnings` — coverage gaps, partial failures, caveat strings

#### The Four-Stage Loop (per task)

```
for each InvestigationTask in plan.tasks (up to maxParallelTasks concurrently):
  Stage 1 — CHOOSE:  select the registered WorkerType for this task
  Stage 2 — EXECUTE: call InvestigationTool.execute(context, task) — timeout 10s
  Stage 3 — VALIDATE: check evidence business_unit, window, data version, metric definition
  Stage 4 — PROGRESS: decide — enough evidence? budget left? one bounded follow-up justified?
```

**Follow-up rule:** AI may request one bounded follow-up only when:
1. The current evidence is partial (e.g. vendor breakdown needed after overall delay confirmed)
2. `toolCallsUsed < maxToolCalls` AND `depth < maxDepth`
3. The follow-up is for the same tenant/scope — never cross-tenant

**Stop rules:**
- `toolCallsUsed >= maxToolCalls` → stop, mark remaining tasks as `pending`
- `depth >= maxDepth` → stop current task branch
- Tool timeout (10s) → mark task as `partial` or `failed`, preserve completed evidence
- No allowed analysis can help → mark task `failed` with reason

---

### Phase D — Design

#### Component Map

```
InvestigationAgentImpl (@Service)
    │
    ├── WorkerRegistry          — maps WorkerType → InvestigationTool impl
    │       ├── VendorWorker
    │       ├── SiteShiftDirectionWorker
    │       ├── DelayReasonWorker
    │       ├── CostBillingWorker
    │       ├── FeedbackWorker
    │       ├── TrackingSafetyWorker
    │       └── NoShowRosterWorker
    │
    ├── BudgetTracker           — thread-safe counter for toolCalls, depth
    ├── TaskExecutor            — runs tasks with bounded concurrency + timeout
    └── EvidenceValidator       — validates MetricEvidence before accepting
```

#### Package Layout

```
workflow/
  agents/
    impl/
      InvestigationAgentImpl.java       ← @Service implements InvestigationAgent
  investigation/
    registry/
      WorkerRegistry.java               ← Spring-managed registry
    workers/
      VendorWorker.java                 ← implements InvestigationTool<MetricEvidence>
      SiteShiftDirectionWorker.java
      DelayReasonWorker.java
      CostBillingWorker.java
      FeedbackWorker.java
      TrackingSafetyWorker.java
      NoShowRosterWorker.java
    budget/
      BudgetTracker.java                ← AtomicInteger counters, deadline check
    executor/
      TaskExecutor.java                 ← CompletableFuture pool, timeout wrapper
    validation/
      EvidenceValidator.java            ← scope, window, version checks
```

#### Seven Workers and Their Data Sources

| Worker | `WorkerType` | Primary dataset | Key columns | What it answers |
|---|---|---|---|---|
| Vendor | `VENDOR` | `ride_data_trip` | `vendor_id`, `delay_minutes`, `delay_reason` | Did one vendor worsen or all? |
| Site/Shift/Direction | `SITE_SHIFT_DIRECTION` | `ride_data_trip` | `office`, `shift_type`, `trip_direction` | Where/when is concentration? |
| Delay Reason | `DELAY_REASON` | `ride_data_trip` | `delay_reason`, `delay_minutes` | Which reason categories drive delays? |
| Cost/Billing | `COST_BILLING` | `bill_data` | `trip_cost`, `total_trip_km`, `vendor`, `contract` | What do billed-cost measures show? |
| Feedback | `FEEDBACK` | `trip_feedback` | `route_rating`, `driver_rating`, `safety_rating` | What do ratings show + coverage? |
| Tracking/Safety | `TRACKING_SAFETY` | `alerts_data` | `event_type`, `severity`, `state_text` | Alert rate changes — regime or ops? |
| No-show/Roster | `NO_SHOW_ROSTER` | `emp_data` | `is_no_show`, `not_boarding_reason`, `boarding_status` | No-show and eligibility rates |

#### Data Normalisation Rules (mandatory before any join)

```java
// trip_id: strip commas, cast to long
tripId = raw.replace(",", "").trim()  → Long.parseLong()

// delay_minutes: strip commas → int
delayMinutes = raw.replace(",", "").trim() → Integer.parseInt()

// trip_cost (bill_data): strip commas → BigDecimal

// Epoch columns (ride_data_trip): strip commas → long
// Epoch columns (emp_data): already float64 — cast to long

// Dates:
//   ride_data_trip.trip_date → "May 1, 2026" → DateTimeFormatter.ofPattern("MMMM d, yyyy")
//   emp_data.trip_date       → ISO "2026-07-09" → LocalDate.parse()
//   trip_feedback.trip_date  → "June 3, 2026, 11:00 AM" → parse with time

// is_driver_nc / is_cab_nc (May file): object with nulls → treat null as unknown, not False
// planned_km / traveled_km: negatives → invalid, exclude from metric calculations
// stwid == 0 → placeholder, exclude from per-employee analyses
// alerts_data.severity == "False" → clean to null
```

#### Tenant Scoping (critical)

Every DuckDB query MUST include `WHERE business_unit = ?` bound to `context.tenant().businessUnit()`. Evidence from the wrong business unit must be rejected by `EvidenceValidator` even if the tool returns it.

---

### Phase L — Loop Design (Bounded Execution)

```
InvestigationAgentImpl.investigate(context, plan):
  budget = new BudgetTracker(context.budget())
  executor = new TaskExecutor(context.budget().maxParallelTasks())

  completedEvidence = []
  completedTasks = []
  pendingTasks = []
  warnings = []

  for batch in partitionByDependency(plan.tasks):
    futures = []
    for task in batch:
      if budget.toolCallsExhausted() or budget.deadlineExceeded():
        pendingTasks.add(task)
        continue
      futures.add(executor.submit(() -> runTaskLoop(context, task, budget, warnings)))

    results = executor.awaitAll(futures, context.budget().investigationTimeout())
    completedEvidence.addAll(results.evidence)
    completedTasks.addAll(results.completed)
    pendingTasks.addAll(results.pending)

  return new InvestigationResult(completedEvidence, completedTasks, pendingTasks, warnings)

runTaskLoop(context, task, budget, warnings):
  depth = 0
  taskEvidence = []

  while depth < budget.maxDepth():
    // Stage 1: CHOOSE
    tool = workerRegistry.resolve(task.worker())  // throws if not registered
    if tool is null: mark failed, break

    // Stage 2: EXECUTE (with 10s timeout)
    raw = tool.execute(context, task)  // catches TimeoutException
    budget.incrementToolCalls()

    // Stage 3: VALIDATE
    if not evidenceValidator.isValid(raw, context):
      warnings.add("rejected evidence: scope mismatch for task " + task.taskId())
      mark failed, break

    taskEvidence.add(raw)

    // Stage 4: PROGRESS
    if sufficientEvidence(taskEvidence) or not budget.canFollowUp():
      break
    task = buildFollowUp(task, taskEvidence)  // same tenant, same scope
    depth++

  return taskEvidence
```

---

### Phase C — Contracts and Data Boundaries

#### `InvestigationTool<MetricEvidence>` contract each worker must honour

```java
MetricEvidence execute(RunContext context, InvestigationTask task)
```

- `evidence.request().tenant()` MUST equal `context.tenant()`
- `evidence.status()` must be `AVAILABLE`, `PARTIAL`, or `UNAVAILABLE` — never null
- `evidence.warnings()` must include coverage < threshold note when applicable
- UNAVAILABLE evidence carries no numeric value — `value` is null, `warnings` explains why
- Must complete within 10 seconds or throw `InvestigationTimeoutException`

#### Feedback worker special rules (rating-zero policy)

- Rating `0` means "unrated" for legacy rows — exclude from mean/distribution
- Low coverage (< 20% of trips rated) MUST appear in `warnings`
- `driver_rating` and `safety_rating` are separate dimensions; do not conflate

#### Tracking/Safety worker special rules (G3 case)

- Cross-reference `acknowledge_time` and `state_text` to distinguish open vs closed alerts
- A spike in `DEVICE_NOT_REACHABLE` or `SUPPLEMENTARY_ALERT` may indicate a **recording regime change**, not an operational incident — flag in `warnings`, DO NOT escalate as operational anomaly
- `severity == "False"` rows → clean to null, exclude from severity counts

#### No-show worker special rules

- Use `emp_data.is_no_show` and `not_boarding_reason` — do not derive from `ride_data_trip.noshow_cnt` alone
- Exclude `stwid == 0` rows
- Denominator = eligible employee-leg records for the scoped period, not total trips

---

### Phase D2 — Debug Guide

#### Common failure modes

| Symptom | Root cause | Fix |
|---|---|---|
| `evidence.request().tenant()` mismatch | Worker query not scoped to `context.tenant()` | Add `WHERE business_unit = :bu` to every query |
| `NumberFormatException` on `trip_id` join | Comma-formatted string not stripped | Apply normalisation before join |
| Feedback average wrong | `0` rated rows included | Filter `WHERE rating > 0` or document policy choice |
| Alert spike misclassified as operational | `DEVICE_NOT_REACHABLE` not flagged as regime change | Add regime-change detection to `TrackingSafetyWorker` |
| Budget overrun | Follow-up loop not checking `budget.canFollowUp()` | Check at Stage 4 before building follow-up |
| Task hangs past 10s | `tool.execute()` not wrapped with timeout | Use `CompletableFuture.get(10, SECONDS)` |
| `InvestigationResult.completedTasks` includes unexecuted tasks | Plan tasks copied into completed before execution | Only add to `completedTasks` after `tool.execute()` returns |
| Negative `planned_km` causes wrong no-show denominator | `emp_data` negative distances not filtered | Exclude `planned_km < 0` and `traveled_km < 0` rows |
| `planned_km` cast fails (July file) | July file has comma-formatted object for `planned_km` | Strip commas before parse in `ride_data_trip` loader |
| Cross-month type drift for `is_driver_nc` | May file is `object`, June/July are `bool` | Coerce to Boolean at ingestion, null = unknown |

#### Acceptance checks (from requirement.md)

| Gate | Test |
|---|---|
| G1 — single vendor blame rejected | Feed a plan where all vendors degraded; assert `warnings` contains "universal vendor claim requires qualification" |
| G1 — site/shift concentration found | Feed July delay data; assert `SiteShiftDirectionWorker` returns evidence for ≥1 office/shift combo |
| G2 — unavailable metric caveat | Disable GPS capability; assert `TrackingSafetyWorker` returns `UNAVAILABLE` with explanation in `warnings` |
| G3 — regime change not escalated | Insert spike in `DEVICE_NOT_REACHABLE`; assert `warnings` contains regime-change note, not operational-escalation |
| Budget enforcement | Set `maxToolCalls=2`; assert no more than 2 tool calls made across all tasks |
| Timeout safety | Mock a worker to sleep 15s; assert task marked `partial` within 10s + buffer |
| Tenant isolation | Run two contexts with different `business_unit`; assert no evidence cross-contamination |

---

## 3. Implementation Order

| Step | What | Why first |
|---|---|---|
| 1 | `BudgetTracker` | All workers depend on it |
| 2 | `EvidenceValidator` | Gate before any evidence accepted |
| 3 | `WorkerRegistry` | Required by `InvestigationAgentImpl` |
| 4 | `VendorWorker` + `SiteShiftDirectionWorker` | Core G1 acceptance path |
| 5 | `InvestigationAgentImpl` four-stage loop | Wires everything together |
| 6 | `DelayReasonWorker` + `NoShowRosterWorker` | G1 completion |
| 7 | `FeedbackWorker` | G2 coverage caveat |
| 8 | `TrackingSafetyWorker` | G3 regime change |
| 9 | `CostBillingWorker` | Billing evidence |
| 10 | `TaskExecutor` parallel + timeout | Full bounded execution |
| 11 | Integration test (G1 → G3) | Acceptance gate |

---

## 4. Dependencies on Other Work-Streams

| Dependency | Owner | What Agent 2 needs |
|---|---|---|
| `MetricService` / `GovernedMetricService` impl | WS1 | DuckDB-backed workers; Agent 2 calls these |
| `TenantContext` / `ActorContext` populated | WS3 | `RunContext` must arrive already authorized |
| `WorkflowCheckpoint` persistence | WS3/WS4 | Resume support after partial run |
| `CapabilityMatrix` | WS1 | Which workers are enabled for this tenant |
| Frozen `MetricEvidence` record contract | Integration owner | Do not change fields without producer+consumer review |

---

## 5. What Agent 2 Must NOT Do

- Generate SQL or call DuckDB directly — delegate to WS1 tools only
- Call LLM to compute a metric value
- Accept evidence from a different tenant
- Continue past `maxToolCalls` or `maxDepth`
- Mark a task `completed` unless `tool.execute()` actually returned
- Execute on stale evidence (version mismatch → reject)
- Return `null` warnings list — always return an empty list, never null
