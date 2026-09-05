# Claude Packet — WS4 Product API

You own reporting, contextual questions and thin REST delivery for the MoveInSync Mobility Decision Copilot.

## Baseline

- Base: `Java-branch-2` at `<BASELINE_COMMIT>`
- Branch: `feat/product-api`
- Worktree: `<WORKTREE_PATH>`

## Read first

- `AGENTS.md`
- `SESSION_CONTEXT.md`
- `docs/parallel-delivery-plan.md`
- `contracts/openapi/mobility-copilot.yaml`
- `docs/hackathon-decision-register.md`

## Exclusive write scope

```text
backend/src/main/java/.../reporting/**
backend/src/main/java/.../conversation/**
backend/src/main/java/.../api/**
backend/src/test/java/.../reporting/**
backend/src/test/java/.../conversation/**
backend/src/test/java/.../api/**
```

## Goal

Expose stable, tenant-safe REST endpoints and two evidence-consistent report formats. Keep conversation contextual and subordinate to the proactive workflow.

## Required work

1. Implement the frozen morning-brief, question, workflow, approval and audit endpoints.
2. Keep controllers thin and dependent on application interfaces only.
3. Map one evidence bundle into operational and leadership outputs.
4. Support healthy, anomalous, unsupported and degraded responses.
5. Include workflow/trace and evidence references.
6. Limit “Ask about this” to current authenticated context and governed tools.
7. Return stable validation, authorization, capability and dependency errors.

## Forbidden

- No JDBC, SQL or metric calculation in controllers.
- No uncited numeric claim.
- No duplicate workflow or approval logic.
- No general-purpose web/SQL chatbot.
- No edits to OpenAPI/contracts, build files, docs or other workstreams; request changes through the Integration Owner.

## Acceptance

- OpenAPI provider tests pass.
- Every analytical response carries evidence and trace/workflow identifiers.
- Invalid tenant, approval and unsupported-capability cases fail safely.
- Operations and leadership facts cannot diverge.
- API integration tests pass.

## Delivery

Commit only your owned paths. Do not push or merge into `Java-branch-2`. Return the standard handoff and list every endpoint demonstrated.
