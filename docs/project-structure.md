# Mobility Decision Copilot — Project Structure

## 1. Decision

Use a Java-first monorepo with:

- one Spring Boot backend;
- one React and TypeScript frontend;
- DuckDB as the read-only analytical plane over the organizer CSVs;
- PostgreSQL for workflow checkpoints, approvals, idempotency and audit;
- four logical LLM roles inside one controlled workflow;
- versioned prompts, governed SQL, API contracts and evaluation fixtures;
- OpenTelemetry traces exported to Langfuse.

The four agents are application components, not independently deployed services. This keeps the hackathon implementation simple while preserving clear boundaries that can later be separated if scale requires it.

## 2. Recommended repository tree

```text
mobility-decision-copilot/
├── README.md
├── AGENTS.md
├── SESSION_CONTEXT.md
├── pom.xml                         # parent Maven build
├── mvnw
├── mvnw.cmd
├── .mvn/
├── package.json                    # optional root shortcuts only
├── compose.yaml                    # app + PostgreSQL + telemetry dependencies
├── .env.example                    # names only; never secrets
├── .gitignore
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/moveinsync/mobilitycopilot/
│       │   │   ├── MobilityCopilotApplication.java
│       │   │   ├── config/         # Spring wiring and typed configuration
│       │   │   ├── access/         # tenant identity, roles and authorization
│       │   │   ├── ingestion/      # CSV discovery, validation and normalization
│       │   │   ├── metrics/        # M01-M18 registry and governed metric service
│       │   │   ├── anomaly/        # baselines, thresholds and regime-change rules
│       │   │   ├── workflow/
│       │   │   │   ├── domain/
│       │   │   │   │   ├── WorkflowState.java
│       │   │   │   │   ├── WorkflowStep.java
│       │   │   │   │   ├── WorkflowOutcome.java
│       │   │   │   │   └── InvestigationTask.java
│       │   │   │   ├── application/
│       │   │   │   │   ├── WorkflowEngine.java
│       │   │   │   │   └── WorkflowCoordinator.java
│       │   │   │   ├── agents/
│       │   │   │   │   ├── SupervisorAgent.java
│       │   │   │   │   ├── InvestigationAgent.java
│       │   │   │   │   ├── EvidenceCriticAgent.java
│       │   │   │   │   └── BriefingActionAgent.java
│       │   │   │   ├── nodes/
│       │   │   │   │   ├── deterministic/  # auth, metrics, policy, approval, audit
│       │   │   │   │   └── generative/     # plan, investigate, critique, explain
│       │   │   │   ├── investigation/
│       │   │   │   │   ├── InvestigationTool.java
│       │   │   │   │   └── workers/
│       │   │   │   │       ├── VendorWorker.java
│       │   │   │   │       ├── SiteShiftDirectionWorker.java
│       │   │   │   │       ├── DelayReasonWorker.java
│       │   │   │   │       ├── CostBillingWorker.java
│       │   │   │   │       ├── FeedbackWorker.java
│       │   │   │   │       ├── TrackingSafetyWorker.java
│       │   │   │   │       └── NoShowRosterWorker.java
│       │   │   │   └── adapter/
│       │   │   │       ├── langgraph4j/     # used only if the spike passes
│       │   │   │       └── statemachine/    # deterministic fallback
│       │   │   ├── evidence/       # evidence bundles, citations and verification
│       │   │   ├── reporting/      # morning brief and leadership report
│       │   │   ├── action/         # action proposals and mocked executors
│       │   │   ├── approval/       # pause/resume, revalidation and decisions
│       │   │   ├── audit/          # append-only business audit events
│       │   │   ├── conversation/   # contextual “Ask about this” use case
│       │   │   ├── observability/  # OTel attributes, redaction and trace helpers
│       │   │   └── shared/         # only small, genuinely shared primitives
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── prompts/v1/
│       │       │   ├── supervisor.md
│       │       │   ├── investigator.md
│       │       │   ├── evidence-critic.md
│       │       │   └── briefing-action.md
│       │       ├── sql/
│       │       │   ├── schema/      # DuckDB staging definitions
│       │       │   ├── views/       # normalized tenant-keyed views
│       │       │   └── metrics/     # one reviewed SQL file per metric
│       │       └── db/migration/    # PostgreSQL Flyway migrations
│       └── test/
│           ├── java/com/moveinsync/mobilitycopilot/
│           │   ├── unit/
│           │   ├── integration/
│           │   ├── contract/
│           │   ├── golden/
│           │   ├── security/
│           │   └── architecture/   # ArchUnit dependency rules
│           └── resources/fixtures/
│
├── frontend/
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
│       ├── main.tsx
│       ├── app/                     # providers, router and application shell
│       ├── core/                    # auth, typed API client and shared state
│       ├── shared/                  # reusable UI primitives only
│       └── features/
│           ├── morning-brief/
│           ├── anomaly-investigation/
│           ├── conversation/
│           ├── approval-inbox/
│           ├── audit-trail/
│           └── trust-panel/
│
├── contracts/
│   ├── openapi/mobility-copilot.yaml
│   └── schemas/
│       ├── evidence-bundle.schema.json
│       ├── decision-brief.schema.json
│       ├── action-proposal.schema.json
│       └── approval-decision.schema.json
│
├── data/
│   ├── README.md                    # documents MOBILITY_DATA_DIR
│   ├── fixtures/                    # tiny deterministic test inputs
│   └── corrupted/                   # generated V1-V5 copies, never originals
│
├── evals/
│   ├── golden/                      # G1, G2 and G3 expected trajectories
│   ├── adversarial/                 # tenant, tool and prompt-abuse cases
│   └── expected/                    # machine-checkable expected outcomes
│
├── infra/
│   ├── docker/
│   └── aws/                         # only the deployment assets actually used
│
├── scripts/
│   ├── data/                        # offline profiling and fixture generation
│   ├── dev/
│   └── demo/
│
├── docs/                            # source-of-truth plans and decisions
├── outputs/
│   └── official dataset/            # existing immutable organizer input
└── .github/workflows/
    ├── build.yml
    └── evaluation.yml
```

Use the actual package namespace agreed with the team; the tree uses `com.moveinsync.mobilitycopilot` as an illustrative namespace.

## 3. How the image maps to this project

| Generic image folder | Project-specific replacement | Reason |
|---|---|---|
| `requirements.txt` | Maven `pom.xml` files and `frontend/package.json` | The runtime is Java plus React/TypeScript, not Python. |
| `src/agent/` | `backend/.../workflow/agents/` | Contains the four logical roles. |
| `agent/state.py` | `workflow/domain/WorkflowState.java` | One typed, serializable state contract. |
| `agent/executor.py` | `WorkflowEngine` plus graph/state-machine adapters | Keeps the framework replaceable. |
| `agent/memory.py` | PostgreSQL checkpoint and conversation repositories | Memory is durable application state, not an agent singleton. |
| `src/tools/` | `workflow/investigation/` plus domain application ports | Every tool has tenant scope, typed input/output and bounded result size. |
| `src/models/` | Records beside their owning feature | Avoids an unbounded “models” dumping ground. |
| `models/embeddings.py` | Omitted | The official data is structured CSV; no embedding pipeline is required. |
| `src/prompts/` | `backend/src/main/resources/prompts/v1/` | Prompts are versioned runtime resources. |
| `src/utils/` | Narrow `shared/` primitives only | Business behavior remains in named capabilities. |
| `src/api/` | Thin controllers in the owning feature | API transport does not contain business logic. |
| `data/knowledge_base/` | Omitted | No document corpus, RAG, vector DB, reranker or OpenKB is in scope. |
| `logs/` | Structured stdout and OpenTelemetry | Runtime logs and traces must not be committed. |
| `main.py` | `MobilityCopilotApplication.java` | Spring Boot entry point. |

## 4. Boundary rules

1. Controllers authenticate, validate and call application services; they do not query DuckDB or invoke an LLM directly.
2. The workflow invokes typed application ports. It never constructs SQL from model-generated text.
3. Metric formulas live in the governed metric registry and reviewed SQL, not in prompts.
4. Every analytical key and evidence item carries `business_unit`; trip joins use `(business_unit, trip_id)`.
5. DuckDB is read-only analytics. PostgreSQL holds configuration, checkpoints, approvals, idempotency keys and audit events; it is not the metric engine.
6. Authorization, anomaly thresholds, evidence verification, action policy, approval transitions and audit writes are deterministic.
7. Only the Investigator can iterate, and its tool loop has a configured maximum step count.
8. Agents return typed records validated before the next node runs.
9. `WorkflowEngine` is project-owned. LangGraph4j and the fallback state machine implement that interface, so the application does not depend on graph-framework types.
10. Prompts, model IDs, metric versions, data version and trace ID are captured with every decision run.

## 5. Persistence ownership

| Store | Owns | Must not own |
|---|---|---|
| Organizer CSVs | Immutable source data | Workflow or audit state |
| DuckDB | Normalized views, governed metric queries, cached analytical snapshots | Approvals, action state or source-of-truth audit |
| PostgreSQL | Tenant configuration, workflow checkpoints, approval decisions, action idempotency and append-only audit | Ad-hoc analytical fact processing |
| Langfuse/OTel backend | Traces, latency, token/cost and redacted diagnostic metadata | Business audit truth or secrets |

The runtime receives the organizer data location through `MOBILITY_DATA_DIR`. Do not move or rewrite files under `outputs/official dataset/`.

## 6. Test placement

- **Unit:** formulas, anomaly rules, state transitions and serializers.
- **Integration:** DuckDB CSV/view loading, PostgreSQL repositories, Spring AI structured outputs and trace propagation.
- **Contract:** OpenAPI and JSON-schema compatibility between React and Spring Boot.
- **Golden:** G1, G2 and G3 end-to-end outcomes and M01-M18 fixtures.
- **Security:** tenant isolation, unauthorized tool requests, prompt injection and excessive-agency attempts.
- **Architecture:** ArchUnit rules that keep API, workflow, domain and adapters within their boundaries.
- **Evaluation:** narrative faithfulness and usefulness, using deterministic gates first and an LLM judge only for subjective explanation quality.

## 7. What not to create

- separate microservices for each agent;
- a vector database, embeddings package, RAG pipeline or reranker without a document corpus;
- a general-purpose `utils` or `models` dumping ground;
- model-generated SQL execution;
- a committed `.env`, database file, trace dump or runtime `logs/` directory;
- a second Python runtime service;
- AWS resources that are not needed for the demonstrated deployment.

## 8. Scaffolding order

1. Create the root build, backend, frontend, contracts and test skeleton.
2. Define typed state/output contracts and the `WorkflowEngine` port.
3. Implement CSV ingestion and reproduce the deterministic metric fixtures in DuckDB.
4. Add anomaly detection and evidence bundles.
5. Implement the deterministic state-machine golden path, then run the LangGraph4j spike against the same contract.
6. Add PostgreSQL checkpoints, approvals, revalidation, idempotency and audit.
7. Add OpenTelemetry/Langfuse spans around the workflow, agent and tool boundaries.
8. Build the React morning brief, investigation, approval and trust views.
9. Add G2/G3, corrupted-data, adversarial and conversational flows only after G1 is stable.

This sequence delivers one vertical, judge-visible path before optional breadth.

## 9. Scaffold status (2026-09-05)

The tree is now instantiated. The executable sample includes:

- a Java 21/Spring Boot service with typed workflow state and four logical role classes;
- a project-owned deterministic `WorkflowEngine` implementation and an empty gated LangGraph4j adapter location;
- M01 executed through DuckDB against `data/fixtures/Ride_data _trip-sample.csv`;
- deterministic anomaly and evidence checks ending in `AWAITING_APPROVAL`;
- a React/TypeScript morning-brief screen and typed API client;
- PostgreSQL/Flyway migration samples, public JSON schemas, golden/adversarial cases, Docker assets and CI;
- `scripts/verify.sh` and `scripts/demo/verify-api.sh` as repeatable verification gates.

The scaffold is an implementation seam, not the finished challenge solution. Full official-data ingestion, M01-M18, the eighteen-node workflow, persistence, approval execution, Langfuse traces and the remaining React features are still to be implemented in the recorded order.
