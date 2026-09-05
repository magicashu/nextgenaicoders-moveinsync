---
name: hackathon-persistence-recovery
description: Add or review LangGraph checkpoints, retries, timeouts, idempotency, resume behavior, compensation, and failure recovery. Use when workflows pause, crash, call unreliable tools, or execute side effects.
---

# Hackathon Persistence and Recovery

Make resumption safe before adding retries. A retried or resumed workflow must not duplicate an external action.

## Workflow

1. Identify durable/transient state, side effects, and each operation's idempotency key.
2. Use a durable checkpointer outside local demos and a stable tenant-safe `thread_id`.
3. Put non-deterministic/external calls inside checkpointed tasks or nodes and return JSON-serializable results.
4. Retry only known transient failures with bounded attempts, timeout, backoff, and a final recovery route.
5. Enforce idempotency at the downstream write boundary with a unique action key and persisted status.
6. Put side effects after interrupts; resumption restarts the containing node.
7. Preserve successful parallel results when another branch fails where supported.
8. Test crash before/after writes, timeout, rate limit, corrupted output, retry exhaustion, resume, and duplicate resume.

Read [references/source-notes.md](references/source-notes.md) for checkpoint, interrupt, replay, and error-handling invariants.
