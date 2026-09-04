# Codex CLI Session Handoff

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

- `docs/hackathon-decision-register.md` — accepted, conditional, deferred, and superseded decisions (D-001 through D-038).
- `docs/live-problem-statement-analysis.md` — exact live requirements, rubric mapping, architecture, golden demo, and scope cuts.
- `docs/detailed-solution-architecture-plan.md` — frozen agent count, node map, state/tools, worker tasks bound to the dataset, controls, tests, and build order.
- `docs/project-structure.md` — implementation-ready Java/React monorepo tree, ownership boundaries, test placement and scaffolding order.
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
- Parallel delivery (D-037): six exclusive component workstreams plus one Integration Owner; freeze contracts and metric semantics before creating worktrees; workers do not merge or push directly into `Java-branch`.
- Codex coding ownership (D-038): Codex owns C0-C7—shared contracts/ports, build/configuration, Spring composition, vertical/action/UI integration and release engineering—while Claude owns component packets 01–06.
- Action scope: mocked vendor escalation, investigation ticket, watchlist, or communication draft. No real external communication.
- Observability: one end-to-end trace with nested orchestration, tool, metric, and approval spans.
- Evaluation (D-033): ten deterministic fixtures, corrupted variants V1-V5, and trajectory/narrative cases for G1-G3; LLM judges only for explanation quality.

## Current state

- Live problem statement analyzed; decisions D-015 through D-038 recorded.
- Official dataset received, checksummed (`tmp/profile/checksums.sha256`), and profiled; raw profile outputs in `tmp/profile/*.txt` and parquet caches in `tmp/profile/*.parquet` (regenerable, not authoritative).
- The synthetic rehearsal package under `outputs/01a0584b-8bd8-7370-bc91-86525608d54d/` is retired (D-022/D-023 superseded).
- A verified application scaffold now exists. `scripts/verify.sh` passes the Java unit test, React build/test and fixture check; `scripts/demo/verify-api.sh` passes the live Spring/DuckDB JSON check.
- The next step is replacing the tiny fixture seam with full seven-file ingestion and reconciling official-data M01/M04/M09.
- The execution order, parallel branches, worktree ownership, merge gates and Claude prompts are frozen in `docs/parallel-delivery-plan.md`; Phase 0 metric/contract clarification is the next coordination gate.

## Immediate build sequence

1. **Started:** typed Java state/output records, JSON schemas and replaceable `WorkflowEngine` seam.
2. **Next:** DuckDB adapter for all seven CSVs, normalization, tenant-keyed views and the ten official fixtures.
3. Extend the metric registry from sample M01 to official M01, M04 and M09 for `pinnacle-Slc`; then the rest of M01-M18.
4. Daily snapshot cache, anomaly detection with profiled thresholds, G3 regime-change classification.
5. Graph skeleton with the seven workers; G1 through investigation and verified dual brief.
6. Approval, revalidation, idempotency, audit and trace skeleton.
7. React/TypeScript brief, evidence drawer with capability greying, approval inbox, trust panel.
8. G2 with caveats; corrupted variants V1-V5; regression gate.
9. Conversational drawer and peer-comparison questions.

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
