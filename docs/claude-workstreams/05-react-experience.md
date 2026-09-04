# Claude Packet — WS5 React Experience

You own the complete React/TypeScript decision experience for the MoveInSync Mobility Decision Copilot.

## Baseline

- Base: `Java-branch-2` at `<BASELINE_COMMIT>`
- Branch: `feat/react-experience`
- Worktree: `<WORKTREE_PATH>`

## Read first

- `AGENTS.md`
- `SESSION_CONTEXT.md`
- `docs/parallel-delivery-plan.md`
- `contracts/openapi/mobility-copilot.yaml`
- `docs/high-level-design.md`

## Exclusive write scope

```text
frontend/**
```

## Goal

Build the five-minute judge flow against frozen typed fixtures first, then switch to the real API without changing business semantics.

## Required work

1. Morning brief with impact and benchmark.
2. Investigation drill-down and evidence details.
3. Metric definition, filters, population, freshness, confidence and caveats.
4. Approval preview and approve/reject/edit/expire/result states.
5. Audit timeline and trace link without chain-of-thought.
6. Trust panel for versions, capability gaps, latency and model use.
7. Loading, healthy, anomaly, unavailable, empty and error states.
8. Add contextual conversation only after the proactive flow is stable.

## Forbidden

- No metric or business calculation in the browser.
- No hard-coded production claim or invented evidence.
- No raw chain-of-thought, unsafe HTML or hidden approval scope.
- No backend, contract, root dependency or documentation edits.
- Request shared dependency/contract changes from the Integration Owner.

## Acceptance

- Every KPI opens its governed evidence metadata.
- Approval displays scope, evidence timestamp and consequence.
- Chat answers link back to evidence.
- Mock and real API payloads are contract-compatible.
- Responsive demo layout works.
- Type checking, tests and production build pass.

## Delivery

Commit only `frontend/**`. Do not push or merge into `Java-branch-2`. Return the standard handoff with screenshots or route names and exact test/build results.
