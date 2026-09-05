---
name: hackathon-langgraph-orchestration
description: Design or implement the hackathon's LangGraph supervisor, specialist subgraphs, routing, typed state, approval flow, and bounded agent loops. Use for orchestration changes; use the recovery or security skills for dedicated hardening.
---

# Hackathon LangGraph Orchestration

Preserve the accepted live-challenge boundaries: LangGraph orchestrates; Langfuse observes; DuckDB executes governed analytics over the supplied local dataset. Keep business audit/configuration behind a repository interface, with PostgreSQL as the production target and a local store acceptable for the hackathon demo.

## Workflow

1. Classify every business step as deterministic code, LLM reasoning, data retrieval, external action, or human input.
2. Define typed state containing raw durable facts, decisions, evidence references, counters, errors, and execution metadata. Format prompts inside nodes.
3. Prefer deterministic edges for authorization, thresholds, schema checks, retry exhaustion, approvals, and termination. Use the model only for semantic judgment.
4. Give each node one responsibility and typed output. Use `Command` when a node must atomically update state and route.
5. Use the controlled Supervisor, bounded Investigation Agent, Evidence Critic, and Briefing/Action Agent. Add a Knowledge/RAG specialist only when a supplied document corpus passes the recorded activation gate; otherwise governed SQL is the evidence path.
6. Bound every loop with step, retry, time, and cost limits. Route exhaustion to clarification, fallback, or safe failure.
7. Compile with a checkpointer when interrupts, recovery, or thread memory are required.
8. Test every route, terminal state, parallel merge, and approval resume path.

## Invariants

- Authorization occurs before retrieval.
- High-impact actions occur only after deterministic policy checks and approval.
- A verifier receives evidence and structured outputs, not hidden reasoning.
- Specialist state is per invocation unless persistent memory has a demonstrated need.
- Unexpected exceptions surface; only known failures receive automatic handling.

Read [references/source-notes.md](references/source-notes.md) when designing state, choosing multi-agent patterns, using subgraphs, or implementing interrupts.
