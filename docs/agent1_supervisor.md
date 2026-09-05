# Agent 1: Governed Supervisor

## Purpose

Agent 1 plans bounded investigations for one detector-selected mobility issue. It decides which registered analytical workers should run. It does not calculate metrics or execute workers itself.

Implementation: `backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/GovernedSupervisorAgent.java`.

## Inputs

`SupervisorPlanningRequest` contains:

- `RunContext`: run ID, authorized actor, tenant, persona, as-of date, versions, budget and deadline.
- `AnomalyIssue`: detector-selected issue, tenant, data version, category, severity, evidence and caveats.
- `CapabilityMatrix`: supported, caveated or unavailable metric capabilities for the same tenant and data version.
- Optional `userContext`: bounded user-supplied question or data. Maximum 32,000 characters. Treated as untrusted data.

Question planning also accepts a user question and selected `issueId`. The issue source must resolve that issue inside authorized run scope.

## Planning flow

1. Resolve detector-selected issue through `SupervisorIssueSource`.
2. Validate actor tenant scope before planning.
3. Validate tenant and data-version equality across run, issue, capabilities and evidence.
4. Validate run versions, deadline and positive budget values.
5. Select primary usable governed evidence.
6. Build allowlisted worker rules for the primary metric.
7. Remove workers whose metric capability is unavailable.
8. Ask optional LLM to choose among remaining typed worker/metric pairs.
9. Preserve mandatory broad comparisons, especially vendor and site/shift/direction comparisons.
10. Limit task count to available tool-call budget. Each task receives current and prior-four-complete-week requests.
11. Emit `InvestigationPlan` with tasks, required evidence and stop conditions.

## LLM behavior

LLM provider uses `LanguageModelPort` with `AgentRole.SUPERVISOR`.

Sarvam adapter is enabled only with:

```text
SARVAM_ENABLED=true
SARVAM_API_KEY=<server-side key>
```

LLM receives compact issue evidence plus prompt/user context. User data is labeled untrusted. Model output must be JSON containing worker and metric choices. Parser accepts only entries already present in the allowlist.

LLM may choose investigation order and optional branches. It cannot:

- Add worker types or metrics.
- Change tenant, data version, period or filters.
- Generate SQL.
- Calculate governed values.
- Grant permissions.
- Approve or execute actions.
- Treat user data as instructions.

Missing provider, timeout, invalid JSON, unknown worker, unknown metric or provider error uses deterministic fallback. No provider key is required for fallback mode.

## Registered workers

- `VENDOR`: qualified vendor comparison without automatic blame.
- `SITE_SHIFT_DIRECTION`: concentration by supported site, shift and direction dimensions.
- `DELAY_REASON`: recorded delay-reason mix.
- `COST_BILLING`: governed billing evidence.
- `FEEDBACK`: ratings and coverage caveats.
- `TRACKING_SAFETY`: alert/tracking evidence and recording-regime caveats.
- `NO_SHOW_ROSTER`: eligible no-show and roster evidence.

Workers are tools, not additional agents. Investigator owns execution and result validation.

## Outputs

`InvestigationPlan` contains:

- Stable plan ID using issue ID and data version.
- Selected issue ID.
- Ordered `InvestigationTask` list.
- Required current/historical evidence keys.
- Stop conditions for unavailable, malformed, partial or budget-exhausted work.

Each task contains worker, question, two governed metric requests and no generated filters.

## Explicit non-responsibilities

Supervisor does not:

- Read CSV files directly.
- Execute SQL or DuckDB queries.
- Reimplement M01–M18 formulas.
- Detect or prioritize anomalies.
- Run investigation workers.
- Verify final evidence claims.
- Compose final reports.
- Decide policy or propose approval state.
- Execute external or mock actions.
- Persist workflow checkpoints.
- Replace API authorization.

Those responsibilities belong to ingestion/metrics, anomaly, Investigator, Evidence Critic, reporting, access/action and workflow components.

## Safe failure behavior

- Missing issue: reject planning.
- Foreign tenant or data version: reject before tool planning.
- Invalid actor scope: reject before data use.
- Unsupported question without LLM: return unsupported/clarification error.
- Unsupported capability: omit branch, never report zero.
- No supported branch: reject plan as unavailable.
- Provider failure: deterministic plan.
- Budget too small for one current/historical comparison: reject.
- Malformed evidence window: reject.
- Untrusted LLM output: ignore and use fallback.

## Debugging

### Compile and application smoke test

From repository root:

```sh
./mvnw -q -pl backend -DskipTests compile
./mvnw -q test
```

Spring starts with Sarvam disabled by default:

```sh
./mvnw -pl backend spring-boot:run
```

### Test question routing without network or API key

```sh
./mvnw -q -pl backend -DskipTests compile && printf '%s\n' 'import com.moveinsync.mobilitycopilot.workflow.agents.*;' 'System.out.println(new SupervisorQuestionRouter().route("Why did delay increase?"));' '/exit' | jshell --class-path backend/target/classes
```

Expected result includes `SUPPORTED`, `M01_DELAYED_TRIP_RATE`, and delay investigation workers.

### Enable Sarvam locally

Copy ignored local environment template, fill key in `.env`, then export variables before starting Spring:

```sh
cp .env.example .env
set -a; . ./.env; set +a
./mvnw -pl backend spring-boot:run
```

Never commit `.env` or print `SARVAM_API_KEY`. Default endpoint is `https://api.sarvam.ai/v1/chat/completions`; override with `SARVAM_ENDPOINT` when needed.

### Useful checks

```sh
git diff --check
rg "GovernedSupervisorAgent|SupervisorPlanningRequest|LanguageModelPort" backend/src docs
```

The current scaffold has no public supervisor question endpoint and no production `SupervisorIssueSource` implementation. A direct router check proves classification only. Full LLM planning requires an authorized issue source, capabilities, evidence and a configured provider bean.

## Debugging checklist

1. Confirm actor allowed tenant matches `RunContext.tenant`.
2. Confirm issue, capabilities and every evidence request use same tenant and data version.
3. Confirm `SARVAM_ENABLED` and server-side key only when testing provider calls.
4. Inspect provider HTTP status without logging key or raw sensitive evidence.
5. Check structured output contains only allowlisted worker/metric pairs.
6. Check task count against `WorkflowBudget.maxToolCalls / 2`.
7. Confirm Investigator, not Supervisor, executes returned tasks.
8. Treat fallback as expected behavior when provider is absent or invalid.
