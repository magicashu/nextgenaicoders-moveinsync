# Mobility Decision Copilot — Project Structure

> Branch scope: implementation descriptions refer to Java-branch-2. This Java-branch copy transfers documentation only; see [branch scope](architecture-branch-scope.md).

Updated 2026-09-05 under D-048. This is the implemented repository map, not a scaffold checklist. Runtime architecture: [HLD](high-level-design.md) and [detailed node design](detailed-solution-architecture-plan.md).

## Runtime

One Java 21/Spring Boot application, one React/TypeScript frontend, process-local DuckDB analytics and selectable in-memory/PostgreSQL control repositories. The deterministic Java state machine is active. Sarvam is a direct LanguageModelPort adapter; Spring AI and LangGraph4j are not prerequisites for that integration.

## Backend ownership map

Paths below are relative to backend/src/main/java/com/moveinsync/mobilitycopilot/.

| Path | Implemented responsibility |
|---|---|
| api/ | Thin brief-job, brief, workflow, question, approval, audit and demo controllers; DTOs, request context and errors |
| access/ | Registry identities, tenant roles and deterministic authorization |
| config/ | WorkflowCompositionConfiguration, governed adapter wiring, SchedulingConfiguration, WorkflowProperties, capability health |
| ingestion/application/AnalyticsStore.java | Analytical catalog and data-version boundary |
| ingestion/adapter/duckdb/ | CSV discovery/loading/normalization and DuckDbAnalyticsStore |
| metrics/domain/ | M01–M18 registry, query/result contracts and allowed dimensions |
| metrics/application/BoundedMetricCache.java | Bounded TTL/LRU and single-flight metric work |
| metrics/adapter/duckdb/ | DuckDbMetricService, DuckDbCapabilityMatrixService and governed SQL access |
| anomaly/ | Deterministic materiality, baseline, quality/regime rules and contributions |
| workflow/application/ | WorkflowEngine, ResumableWorkflowEngine, coordinator and checkpoint/snapshot ports |
| workflow/application/ports/ | AnalyticsGateway, LanguageModelPort, DetectionSnapshot, WorkerEvidenceDto and TransitionListener |
| workflow/domain/ | WorkflowState, WorkflowRun/Snapshot, RunContext, plan/results, node/transition/usage records |
| workflow/adapter/statemachine/ | Active DeterministicWorkflowEngine |
| workflow/adapter/sarvam/ | Server-side SarvamLanguageModel |
| workflow/agents/ | SupervisorAgent, InvestigationAgent, EvidenceCriticAgent, BriefingActionAgent, ModelAssist and PromptLibrary |
| workflow/nodes/ | EvidenceMerger and ActionPolicyGate; remaining node methods live in the state-machine adapter |
| workflow/investigation/workers/ | WorkerType and WorkerToolRegistry: seven typed gateway worker adapters |
| evidence/ | Claim/evidence records and deterministic EvidenceVerifier |
| reporting/application/ | AsyncBriefService, BriefJobStore, DecisionRunGateway, RunView and BriefRenderer |
| reporting/adapter/ | WorkflowDecisionRunGateway and InMemoryBriefJobStore |
| approval/ | Approval lifecycle, checkpoint repositories and ControlPlaneBeans |
| approval/adapter/postgres/ | JDBC approvals/checkpoints plus JdbcBriefJobStore and JdbcWorkflowSnapshotStore |
| action/ | Proposal/type/status contracts, revalidation, idempotent execution repositories and mock adapters |
| audit/ | Append-only audit ledger and in-memory/PostgreSQL adapters |
| conversation/ | Constrained question intent and scoped evidence reuse/investigation |
| observability/ | TraceRecorder, redaction, timing and optional export |

No seven-class worker hierarchy or nodes/deterministic/nodes/generative tree is required: the current registry and typed node methods implement those responsibilities.

## Resources and contracts

| Path from repository root | Purpose |
|---|---|
| backend/src/main/resources/application.yml | Workflow, queue, Sarvam, cache namespace and cost settings |
| backend/src/main/resources/application-postgres.yml | PostgreSQL runtime profile |
| backend/src/main/resources/prompts/v1/ | Runtime prompts; metadata revision prompts-v1.1 |
| backend/src/main/resources/sql/ | Reviewed schema/view/metric/contribution SQL |
| backend/src/main/resources/db/migration/ | V1–V3 control schema, including async jobs and rich snapshots |
| contracts/openapi/mobility-copilot.yaml | API 0.3.0 including async brief jobs |
| contracts/schemas/ | Evidence, metric, brief, action, approval, receipt and audit schemas |
| contracts/data/official-checksums.sha256 | Immutable organizer-file integrity gate |
| outputs/official dataset/ | Official CSVs and dictionary; never edited or moved |
| data/fixtures/ | Tiny and seven-file test inputs |
| evals/ | Golden, adversarial, recovery, corrupted-input and scorecard artifacts |

## Frontend map

| Path under frontend/src/ | Responsibility |
|---|---|
| App.tsx | Identity/date/view state, request cancellation and stale-response protection |
| app/AppShell.tsx | Responsive workspace shell and controls |
| app/ApiContext.tsx | Typed API provider |
| core/api.ts / contracts.ts | Async polling, transport and backend DTO mirror |
| features/morning-brief/ | MorningBriefPage and memoized EvidenceCharts |
| features/anomaly-investigation/ | Branch findings, capability gaps and evidence table |
| features/conversation/ | AskDrawer |
| features/approval-inbox/ | Approval preview/decisions and receipt |
| features/audit-trail/ | Audit history |
| features/trust-panel/ | Versions, traces, provider/fallback/token/cost readings |
| shared/ | Metric cards, evidence drawers/chips, icons, formatting and useDialogFocus |
| styles.css | Responsive layout, keyboard focus, reduced motion and print rules |
| mocks/ / test/ | Typed fixtures and test setup |

The browser displays governed readings. It does not calculate operational metrics, issue SQL or approve actions without backend validation.

## Storage and boundaries

- DuckDB is owned by the application process for analytical loading/querying; the model's tool surface is read-only.
- PostgreSQL owns shared jobs, checkpoints, rich decision snapshots, approvals, action idempotency/receipts and audit. Current identity/target configuration remains registry/configuration-driven.
- In-memory control adapters support one local demo process. Metric/capability caches remain process-local even in PostgreSQL mode.
- Use (business_unit, trip_id) for trip-level joins and aggregates. Every query/cache/evidence/action carries tenant scope.
- Agents return typed records; metrics, verification, policy and state transitions remain deterministic.
- A run snapshot is not a published dataset; an execution receipt is not incident resolution.
- Authorization precedes retrieval/reuse. No external communications or arbitrary SQL tools are present.
- Framework alternatives and unimplemented incident/publication services are proposals, not directories to scaffold automatically.

## Build and verification entry points

- Root/backend Maven builds and frontend/package.json own dependencies; .env.example documents configuration names.
- scripts/verify.sh: default Java/React, contract and fixture checks.
- scripts/release/verify-release.sh: official-file integrity, governed reconciliation, fixture/official HTTP and evaluation gates.
- scripts/performance/benchmark.py: explicitly separate fresh computation from completed-run reuse.
- Backend tests follow owning capability packages plus integration, contract, security, quality and architecture suites.
- PostgreSQL adapter integration uses the postgres Maven profile and an isolated test database; full application PostgreSQL execution also needs the Spring postgres profile.
- Frontend tests cover briefs, evidence, approval, questions, trust and keyboard interaction.

The integrated features and recorded verification are detailed in [component/node review](component-node-review-2026-09-05.md). Publication rollback, incident lifecycle and persistent DQ drill-down remain proposed extensions in the HLD.
