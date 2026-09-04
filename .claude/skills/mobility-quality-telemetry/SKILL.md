---
name: mobility-quality-telemetry
description: Change evaluation gates, adversarial or recovery corpora, redaction, tracing, Langfuse export, demo smoke/scorecard scripts or CI workflows of the Mobility Decision Copilot (packet 06, feat/quality-telemetry). Use for observability, evals or CI work.
---

# Quality, security and telemetry (WS6)

Owned: `backend/.../observability/**`, tests under `quality/**`, `security/**`, `architecture/**`, `fixtures/quality/**`, `evals/**`, `scripts/demo/**`, `.github/workflows/**`.

## Telemetry
`TraceRecorder` opens one root `mobility_run` trace per run (trace id = run id without dashes so audit events share it) with nested `Span`s (request, authorization, agent, tool, critic, report, approval, revalidation, execution, audit link, model). `Redaction.attributes` drops prompt/completion/secret keys, tokenizes `stwid`/rider ids and scrubs emails, phones, keys and JDBC passwords. `LangfuseOtlpExporter` posts OTLP/JSON to `/api/public/otel/v1/traces` (Basic auth, `x-langfuse-ingestion-version: 4`) from a bounded queue on a daemon thread; outages only mark the exporter degraded. Enable with `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`; otherwise `TraceExporter.InMemory`. Wire WS2's `TransitionListener` to `TraceRecorder.Trace.begin/end` in composition.

## Evaluation
Deterministic evaluators in `quality/Evaluators.java` (schema, evidence support, tenant scope, transitions, idempotency) run on run artifacts; fixtures `g1-run-artifact.json` (clean) and `-tampered.json` (every violation) prove them. `scripts/demo/scorecard.sh` collects live artifacts into `evals/results/` and prints the scorecard; the gated tests (`MetricFixtureGateTest`, `ScorecardTest`, `AdversarialCorpusTest`) consume them. Budgets: CI unit 8 min, evaluation 15 min, smoke 60 s. No LLM judge decides arithmetic, authorization, evidence or actions.

## Scripts
`scripts/demo/smoke.sh` (six endpoints, isolation and bypass checks; `MOBILITY_SMOKE_APPROVE=true` approves once), `verify-api.sh` (boot + demo endpoint + smoke; expected M01 follows `MOBILITY_DATA_DIR`), `generate-corrupted-variants.sh` (V1-V5 into `data/corrupted/generated`, never inside the official directory).
