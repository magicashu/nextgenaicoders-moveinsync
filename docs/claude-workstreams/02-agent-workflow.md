# Claude Packet — WS2 Agent Workflow

You own workflow orchestration and the four logical AI roles for the MoveInSync Mobility Decision Copilot.

## Baseline

- Base: `Java-branch-2` at `<BASELINE_COMMIT>`
- Branch: `feat/agent-workflow`
- Worktree: `<WORKTREE_PATH>`

## Read first

- `AGENTS.md`
- `SESSION_CONTEXT.md`
- `docs/parallel-delivery-plan.md`
- `docs/detailed-solution-architecture-plan.md`
- `docs/hackathon-decision-register.md`

## Exclusive write scope

```text
backend/src/main/java/.../workflow/**
backend/src/main/java/.../evidence/**
backend/src/main/resources/prompts/**
backend/src/test/java/.../workflow/**
backend/src/test/java/.../evidence/**
backend/src/test/resources/fixtures/workflow/**
```

## Goal

Implement the typed, bounded four-role workflow behind the project-owned `WorkflowEngine`. The deterministic Java state machine is the guaranteed path; LangGraph4j is an optional adapter only if its spike passes.

## Required work

1. Implement the agreed 18-node workflow and four-node investigation loop.
2. Implement Supervisor, Investigator, Evidence Critic and Briefing/Action roles.
3. Register seven allowlisted investigation workers through application ports.
4. Limit steps, tool calls, latency, tokens and correction cycles.
5. Preserve successful fan-out branches and qualify failures.
6. Make the critic block unsupported claims and vendor blame in G1.
7. Produce operations and leadership briefs from one evidence bundle.
8. Support healthy, no-action, partial, approval-wait and terminal states.
9. Emit traceable transition events.
10. Run the LangGraph4j routing/fan-out/serialization/pause-resume/trace spike in isolation; retain it only if all gates pass without delaying G1.

## Forbidden

- No direct DuckDB/PostgreSQL access.
- No unbounded loop, hidden agent-to-agent call or LLM authorization.
- No model-generated metric, threshold, SQL or action execution.
- No fifth knowledge/RAG agent.
- No edits to shared contracts, build files, docs or other workstreams.

## Acceptance

- The deterministic engine always works without LangGraph4j.
- Typed output validation occurs at each boundary.
- One correction cycle maximum.
- Approval pauses through ports; side effects occur only after resume and policy checks.
- G1 follows the expected trajectory and conservative action recommendation.
- Workflow unit, trajectory and failure-path tests pass.

## Delivery

Commit only your owned paths. Do not push or merge into `Java-branch-2`. Return the standard handoff, including the explicit LangGraph4j keep/drop result.
