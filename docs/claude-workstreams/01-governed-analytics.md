# Claude Packet — WS1 Governed Analytics

You own governed ingestion, metrics and anomalies for the MoveInSync Mobility Decision Copilot.

## Baseline

- Base: `Java-branch-2` at `<BASELINE_COMMIT>`
- Branch: `feat/governed-analytics`
- Worktree: `<WORKTREE_PATH>`
- Java 21/Spring Boot; DuckDB is the analytical plane.

## Read first

- `AGENTS.md`
- `SESSION_CONTEXT.md`
- `docs/parallel-delivery-plan.md`
- `docs/dataset-profile-and-capability-matrix.md`
- `docs/hackathon-decision-register.md`

## Exclusive write scope

```text
backend/src/main/java/.../ingestion/**
backend/src/main/java/.../metrics/**
backend/src/main/java/.../anomaly/**
backend/src/main/resources/sql/**
backend/src/test/java/.../ingestion/**
backend/src/test/java/.../metrics/**
backend/src/test/java/.../anomaly/**
backend/src/test/resources/fixtures/analytics/**
data/fixtures/**
data/corrupted/**
```

## Goal

Return tenant-safe, versioned and reproducible metric/anomaly evidence from all seven immutable official CSVs. Build the G1 slice before breadth.

## Required work

1. Discover, validate and normalize all seven files.
2. Enforce `(business_unit, trip_id)` in every trip join.
3. Implement G1 M01/M03/M04/M09/M11 from reviewed SQL.
4. Reconcile the ten deterministic fixtures and exact G1 output.
5. Return metric provenance, supporting count, coverage and caveats.
6. Add daily snapshots, capability status and healthy-state behavior.
7. Implement anomaly candidates and seven contribution tools.
8. Detect G3 as a data-regime change.
9. Add the remaining M01-M18 only after G1 is green.

## Forbidden

- No LLM calls or free-form SQL.
- No `trip_id`-only join.
- No approval/audit writes.
- No edits to official files, shared contracts, build files, docs or other workstreams.
- No RAG, embeddings, reranking, vector database or OpenKB.
- Do not choose an unresolved metric interpretation; request a contract change.

## Acceptance

- G1 numbers reproduce from DuckDB.
- Tenant-collision and official checksum tests pass.
- Unsupported metrics return a typed reason.
- Outliers, negative bills, zero km and low coverage follow the frozen contracts.
- A healthy input does not produce a forced anomaly.
- Unit and DuckDB integration tests pass.

## Delivery

Commit only your owned paths. Do not push or merge into `Java-branch-2`. Return the standard handoff from the plan, including exact test commands/results and any shared change request.
