# Claude Packet — WS3 Governance and Actions

You own trusted tenant access, workflow durability, approval, action execution and audit for the MoveInSync Mobility Decision Copilot.

## Baseline

- Base: `Java-branch-2` at `<BASELINE_COMMIT>`
- Branch: `feat/governance-actions`
- Worktree: `<WORKTREE_PATH>`

## Read first

- `AGENTS.md`
- `SESSION_CONTEXT.md`
- `docs/parallel-delivery-plan.md`
- `docs/detailed-solution-architecture-plan.md`
- `docs/hackathon-decision-register.md`

## Exclusive write scope

```text
backend/src/main/java/.../access/**
backend/src/main/java/.../approval/**
backend/src/main/java/.../action/**
backend/src/main/java/.../audit/**
backend/src/main/resources/db/migration/**
backend/src/main/resources/application-postgres.yml
backend/src/test/java/.../access/**
backend/src/test/java/.../approval/**
backend/src/test/java/.../action/**
backend/src/test/java/.../audit/**
```

## Goal

Guarantee that tenant scope and every side effect are deterministic, approved, fresh, idempotent, restart-safe and auditable.

## Required work

1. Derive actor/tenant context from trusted server identity.
2. Add PostgreSQL/Flyway repositories for checkpoints, approvals, idempotency and append-only audit.
3. Implement approve, reject, edit and expire transitions.
4. Persist pause/resume state across restart.
5. Revalidate authorization, evidence version and action state after approval.
6. Add idempotent mock executors for watchlist, ticket, escalation and communication draft.
7. Prevent duplicate effects across retry/resume.
8. Preserve approved-not-executed on adapter failure.
9. Record actor, tenant, evidence, decision, timestamps and result in audit events.

## Forbidden

- Never trust tenant supplied in conversation text.
- Never execute before approval or from stale evidence.
- Never make a non-idempotent effect retryable.
- Never update/delete historical audit events.
- No LLM calls or analytical formulas.
- No edits to shared contracts, build files, docs or other workstreams.

## Acceptance

- Cross-tenant access is denied and makes zero tool/action calls.
- Reject/expire/stale states cannot execute.
- Approve triggers revalidation and exactly one effect.
- Crash/resume restores the correct state.
- Duplicate requests return the same receipt.
- PostgreSQL integration and state-machine tests pass.

## Delivery

Commit only your owned paths. Do not push or merge into `Java-branch-2`. Return the standard handoff with migration order and recovery assumptions.
