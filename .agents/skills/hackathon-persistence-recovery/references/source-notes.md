# Persistence and recovery source notes

## Sources reviewed

- [LangGraph persistence](https://docs.langchain.com/oss/python/langgraph/persistence)
- [LangGraph Functional API](https://docs.langchain.com/oss/python/langgraph/functional-api)
- [LangGraph interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)
- [Thinking in LangGraph](https://docs.langchain.com/oss/python/langgraph/thinking-in-langgraph)

## Decisions extracted

- Checkpoints grouped by thread enable human input, memory, replay, and recovery.
- Replay skips prior nodes and re-executes later calls, so later side effects need idempotency.
- Entrypoints/tasks require JSON-serializable I/O. Encapsulate randomness/external calls in tasks for deterministic resume order.
- Interrupt resumption restarts the node. Never place a non-idempotent effect before `interrupt()`.
- Retry transient errors, allow bounded correction for parse/tool errors, interrupt for user gaps, and surface unexpected failures.
- Successful sibling writes may survive another task's failure; verify with the chosen checkpointer/version.

Persist action ID, idempotency key, parameters, evidence version, approval, precondition version, status, receipt, attempts, and timestamps. Recheck authorization/preconditions before execution.
