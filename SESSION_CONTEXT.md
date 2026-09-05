# Codex CLI Session Handoff

## Latest publication direction (2026-09-05)

The user authorized replacing main's implementation with the current try1 tree: native LangGraph4j orchestration, Sarvam adapter, Langfuse tracing and the integrated dashboard design from main commit 1ea1fb8. D-048 records the publication approach. Preserve the ignored local provider environment files; do not restore the incompatible main runtime. See docs/frontend-integration-1ea1fb8.md for build and local UI verification, and docs/try1-setup.md for startup commands.

## Current try1 implementation (D-046, 2026-09-05)

The user explicitly superseded the framework deferral: `LangGraphWorkflowEngine`
now uses LangGraph4j 1.8.25 for the 18 main nodes, native approval interruption and
resume. The old Java state-machine engine was removed. Investigator branches use
four-node LangGraph4j loops. Sarvam is wired through LanguageModelPort; automatic
mode enables it when SARVAM_API_KEY is present. Node decisions and model usage are
visible in the Trust view, the execution endpoint, OTLP/Langfuse and the business
audit ledger. See `docs/try1-setup.md`. Earlier notes below are historical where
they conflict with D-046. The user requested skipping test-case work in this change.

## Mission

Continue the MoveInSync AI hackathon project and produce a judge-ready working prototype for **Agentic Intelligence & Reporting Layer for Enterprise Mobility**. The event is on 2026-09-05. The official dataset arrived on 2026-09-04 and has been profiled.

## Canonical workspace

`/Users/miniorange/Desktop/miniOrange-IAM/try/hackathon`

## GitHub identity

Use the configured GitHub account `magicashu` for this project. Do not switch accounts unless the user explicitly asks.

Start a fresh CLI session with:

```bash
cd /Users/miniorange/Desktop/miniOrange-IAM/try/hackathon
codex
```

Read `AGENTS.md` first. The authoritative records are:

- `docs/hackathon-decision-register.md` — accepted, conditional, deferred, and superseded decisions (D-001 through D-044).
- `docs/live-problem-statement-analysis.md` — exact live requirements, rubric mapping, architecture, golden demo, and scope cuts.
- `docs/detailed-solution-architecture-plan.md` — frozen agent count, node map, state/tools, worker tasks bound to the dataset, controls, tests, and build order.
- `docs/project-structure.md` — implementation-ready Java/React monorepo tree, ownership boundaries, test placement and scaffolding order.
- `docs/integration-gate-board.md` — live workstream intake and release-gate state; worker completion is not release completion.
- `docs/dataset-profile-and-capability-matrix.md` — official dataset inventory, checksums, tenant/key model, join coverage, data-quality findings, field map, per-tenant capability matrix, metric contracts M01-M18, golden anomalies G1-G3, fixtures and corrupted variants.
- `docs/high-level-design.md` plus `docs/architecture/mobility-decision-copilot-hld.svg` and `.png` — component/data-flow specification and shareable HLD.
- `docs/moveinsync-ai-hackathon-winning-playbook.md` — broader preparation, team, evaluation, demo, and learning guidance.
- `docs/parallel-delivery-plan.md` and `docs/claude-workstreams/` — phased dependency graph, exclusive ownership, integration gates, cut rules and ready-to-paste Claude implementation packets.
- `references/problem_explanation_7qdzf3jxklt.pdf` — supplied live problem statement.
- `outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset/` — the seven organizer CSVs and `Dictionary/`. Never modify these files.

Do not reproduce those documents here; use them as the source of truth.

## Frozen decisions that matter now

- Product: **Mobility Decision Copilot**, a proactive operations briefing and intervention agent.
- Primary persona: transport manager. Secondary artifact: leadership-ready report for the transport and facilities head.
- Stack: Java 21, Spring Boot, Spring AI, React with TypeScript, schema adapter, DuckDB JDBC for local analytics, governed metric contracts, Langfuse through OpenTelemetry, and PostgreSQL for production configuration/control/audit. AWS is the target deployment story. Python is limited to offline dataset profiling/validation (scripts under `tmp/profile/`).
- Java orchestration gate: pin a stable LangGraph4j 1.8.x release only after a short spike proves routing, parallel fan-out/fan-in, checkpointed approval resume, state serialization and nested traces. Keep project-owned node/tool interfaces so a deterministic Java state machine can replace LangGraph4j. Do not use the experimental `langchain4j-agentic` module.
- LLM boundary: bounded investigation routing and evidence-grounded explanation. Deterministic code owns profiling, calculations, thresholds, authorization, impact, approvals, and state transitions.
- Orchestration: four LLM specialists (Supervisor, Investigator, Evidence Critic, Briefing/Action). 18 main nodes plus a four-node investigator subgraph. Seven worker tasks: vendor, site-shift-direction, delay reason, cost and billing, feedback, tracking and safety alerts, no-show and roster. There is no GPS worker.
- Dataset bindings (D-029 to D-031): tenant is `business_unit` (five tenants); trip key is `(business_unit, trip_id)` because 6,753 `trip_id` values collide across tenants; epochs are already local wall-clock; metric contracts M01-M18 with capped delays, excluded negative bills and configured (not organizer-supplied) targets; per-tenant capability matrix disables cost per km for `vanta-Aus`/`vanta-Sea` and marks feedback low-coverage for three tenants.
- Golden demo (D-032): G1 `pinnacle-Slc` as-of 2026-06-08, delayed-trip rate 21.9% vs 12.3% May baseline, concentrated in LOGIN trips, Clearwater Campus and 09:00-10:30 shifts, all vendors rising together; action is a site-shift watchlist plus investigation ticket. G2 `vanta-Aus` as-of 2026-08-01 cross-domain deterioration with caveats. G3 sign-off-violation alert step change must be classified as a data-regime change, not escalated.
- Conversational UX (D-027): authenticated **Ask about this** drawer after the proactive path is stable; same four-agent graph, tenant-scoped context, no arbitrary SQL, every action through policy, approval, revalidation, idempotency and audit.
- Structured-data focus: no RAG, reranking, or OpenKB unless a document corpus is supplied.
- Repository structure (D-034/D-035): one Spring Boot service plus one React/TypeScript app, organized by business capability; the four agents are logical roles, `WorkflowEngine` shields the application from LangGraph4j, and there are no generic RAG/vector-store/model/utils packages.
- Scaffold (D-036): runnable Java 21/Spring Boot and React starter exists. Its deterministic sample computes fixture M01 in DuckDB, verifies evidence, drafts a brief and stops at approval. Spring AI and PostgreSQL are opt-in Maven profiles; LangGraph4j is still gated.
- Parallel delivery (D-037/D-040): six exclusive component workstreams plus one Integration Owner; `Java-branch-2` is the implementation/integration branch, while `Java-branch` remains the preserved scaffold/plan baseline; workers do not merge or push directly into `Java-branch-2`.
- Codex coding ownership (D-038): Codex owns C0-C7—shared contracts/ports, build/configuration, Spring composition, vertical/action/UI integration and release engineering—while Claude owns component packets 01–06.
- Metric contracts (D-039): v1.1 fixes M06 to use all valid employee legs, M09 to use median per-trip positive billed cost, M11 to driver-only low ratings, M15 to Sev-1/2 acknowledgement P90, M18 to descriptive escort-present rate, and qualifies universal vendor comparisons at 500 trips in both windows.
- Shared control contracts (D-041): typed authorization, optimistic checkpoint, approval, revalidation, idempotent execution and audit ports plus configured workflow bounds are frozen for Claude consumers.
- Official-data gate (D-042): `scripts/release/verify-release.sh` pins organizer checksums and runs the complete suite plus fixture/official API checks; G1 M01 must reproduce 4,357/19,913 = 21.88%, baseline 12.28%, delta 9.60 pp, with explicit normalization of free-text dates and comma numerics.
- Capability reporting (D-043): Actuator distinguishes process health from `releaseReady`, investigation readiness, governed-action readiness and individual adapter availability; missing worker adapters remain visible rather than silently mocked.
- Integrated runtime (D-044): the six Claude packets are merged on `Java-branch-2`; governed DuckDB analytics, the deterministic 18-node workflow, registry identity/RBAC, resumable approval/action control plane, product API, React UI and redacted telemetry are the default composed path. The in-memory control plane is the local fallback; PostgreSQL and Langfuse remain production/optional profiles.
- Action scope: mocked vendor escalation, investigation ticket, watchlist, or communication draft. No real external communication.
- Observability: one end-to-end trace with nested orchestration, tool, metric, and approval spans.
- Evaluation (D-033): ten deterministic fixtures, corrupted variants V1-V5, and trajectory/narrative cases for G1-G3; LLM judges only for explanation quality.

## Current state

- Active implementation/integration branch: `Java-branch-2`; `Java-branch` is the preserved scaffold/parallel-plan baseline.
- Live problem statement analyzed; decisions D-015 through D-043 recorded.
- Official dataset received, checksummed (`tmp/profile/checksums.sha256`), and profiled; raw profile outputs in `tmp/profile/*.txt` and parquet caches in `tmp/profile/*.parquet` (regenerable, not authoritative).
- The synthetic rehearsal package under `outputs/01a0584b-8bd8-7370-bc91-86525608d54d/` is retired (D-022/D-023 superseded).
- All six Claude workstreams are merged into `Java-branch-2`; their formal handoffs are under `.claude/handoffs/`.
- The composed backend has 127 tests including an official-data end-to-end test that reaches `AWAITING_APPROVAL`, resumes after approval, revalidates and executes one idempotent mock effect with a matching trace.
- The official HTTP smoke and scorecard pass G1, G2, G3, security and audit gates with every zero-tolerance counter at zero. React has seven interaction tests and a passing production build.
- Two consecutive clean aggregate release rehearsals pass. Integration commit `e147ed9` is pushed to `origin/Java-branch-2`; the implementation and integration phase is complete.

## Immediate build sequence

1. Start the official-data backend and React UI for the judge rehearsal.
2. Review generated `evals/results/scorecard.json` and the official G1 trace/audit pair.
3. Rehearse the five-minute G1 story, G2 caveat story and G3 false-anomaly suppression.
4. Configure PostgreSQL or Langfuse only if the deployment/demo environment is ready; the local fallback remains fully functional without them.

## Project-local skills to use

- `hackathon-langgraph-orchestration`: graph state, routing, bounded loops, specialists, approvals.
- `hackathon-rag-metrics`: governed metrics and evidence; retrieval portions only if a document corpus appears.
- `hackathon-langfuse-observability`: trace and privacy design.
- `hackathon-agent-evaluation`: golden cases and regression gates.
- `hackathon-persistence-recovery`: checkpoints, retries, idempotency, and resume.
- `hackathon-agent-security`: tenant isolation, prompt injection, excessive agency, and adversarial tests.
- `hackathon-openkb-long-docs`: conditional only.

Use the smallest applicable set. For any approval-bearing action, combine orchestration, recovery, security, observability, and evaluation.

## Definition of done for the hackathon

- Working local demo on the official dataset reproducing G1 and G2.
- Clear sense→reason→act behavior for the transport manager.
- Every headline metric contextualized against the prior four weeks, a configured target, or peers.
- Evidence-backed cross-domain investigation with confidence and data-quality behavior.
- Proactive trigger and multiple outputs.
- Approval-gated action with idempotency and complete audit trail.
- Visible trace/evaluation proof and credible multi-tenant, latency, and cost story.
- Repository, architecture diagram, README/setup, sample I/O, deck, and demo fallback.

## First response in the next session

Confirm the source-of-truth files were read, confirm the dataset checksums match, and start the build sequence at the first incomplete step without re-litigating the frozen stack or dataset bindings unless new evidence requires it.
