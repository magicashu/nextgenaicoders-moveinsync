# LangGraph source notes

## Sources reviewed

- [Thinking in LangGraph](https://docs.langchain.com/oss/python/langgraph/thinking-in-langgraph)
- [Workflows and agents](https://docs.langchain.com/oss/python/langgraph/workflows-agents)
- [Multi-agent patterns](https://langchain-ai.github.io/langgraph/tutorials/multi_agent/multi-agent-collaboration/)
- [Subgraphs](https://docs.langchain.com/oss/python/langgraph/use-subgraphs)
- [Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)

## Decisions extracted

- Workflows have predetermined paths; agents choose tools dynamically. Mix them deliberately.
- Store data only when downstream steps need it or it is expensive to reconstruct. Keep raw values and format prompts on demand.
- Retry transient failures; expose recoverable tool errors within a bounded loop; interrupt for user-fixable gaps; bubble unexpected failures.
- Use the controlled custom workflow rather than unrestricted handoffs or swarms.
- Per-invocation subgraphs are the default for independent specialist calls and support safe parallel execution.
- Per-thread persistence is only for genuine multi-turn specialist memory.
- `interrupt()` needs checkpointing and `thread_id`. Resumption restarts the node, so code before it may run again.
- Interrupt payloads and resume values must be JSON-serializable; do not catch the interrupt as a normal exception.

## Required graph

`authorize -> classify/plan -> metrics and/or retrieval -> investigate -> verify -> report -> approval if needed -> execute -> audit`

## Review

- Can every edge and terminal state be explained?
- Can any loop continue without a hard budget?
- Can untrusted content reach an action without deterministic validation?
- Do parallel branches merge with explicit reducers and provenance?
- Does interrupt resumption duplicate a side effect?
