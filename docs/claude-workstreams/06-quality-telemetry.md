# Claude Packet — WS6 Quality, Security and Telemetry

You own evaluation, adversarial/recovery testing, observability and demo verification for the MoveInSync Mobility Decision Copilot.

## Baseline

- Base: `Java-branch` at `<BASELINE_COMMIT>`
- Branch: `feat/quality-telemetry`
- Worktree: `<WORKTREE_PATH>`

## Read first

- `AGENTS.md`
- `SESSION_CONTEXT.md`
- `docs/parallel-delivery-plan.md`
- `docs/dataset-profile-and-capability-matrix.md`
- `docs/hackathon-decision-register.md`

## Exclusive write scope

```text
backend/src/main/java/.../observability/**
backend/src/test/java/.../quality/**
backend/src/test/java/.../security/**
backend/src/test/java/.../architecture/**
backend/src/test/resources/fixtures/quality/**
evals/**
scripts/demo/**
.github/workflows/**
```

## Goal

Prove correctness, safety, recovery and cost/latency behavior without making telemetry or an LLM judge part of business correctness.

## Required work

1. Instrument one nested OTel trace across request, authorization, roles, tools, critic, report, approval, revalidation, execution and audit link.
2. Redact secrets/PII and make Langfuse export non-blocking.
3. Add deterministic G1/G2/G3 gates and the ten metric fixtures.
4. Add V1-V5 corrupted-data variants.
5. Test cross-tenant access, prompt injection, forged tool instructions, unsupported claims and approval bypass.
6. Test timeout, retry exhaustion, partial failure, malformed output, crash/resume and duplicate effect.
7. Validate schemas, evidence support, state transitions and idempotency deterministically.
8. Produce compact evaluation results and one-command demo smoke tests.
9. Capture latency, model-call, token and cost measures.

## Forbidden

- Do not change other workstreams' production logic to force a green test.
- Do not log raw employee data, secrets, unrestricted prompts or chain-of-thought.
- Do not make Langfuse required for product execution.
- Do not use an LLM judge for arithmetic, authorization, evidence or action correctness.
- No edits to shared contracts/build files/docs; request them from the Integration Owner.

## Acceptance

- Zero cross-tenant leak, unsupported displayed number, unauthorized action or duplicate effect.
- G3 escalation count is zero.
- Every displayed number resolves to evidence.
- Langfuse outage preserves safe execution and local trace ID.
- CI, release and demo suites have explicit runtime budgets.
- Quality and smoke tests pass.

## Delivery

Commit only your owned paths. Do not push or merge into `Java-branch`. Return the standard handoff with the scorecard path, exact commands/results and any production defect found.
