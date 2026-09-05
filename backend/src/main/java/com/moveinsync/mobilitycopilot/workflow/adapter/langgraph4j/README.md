# LangGraph4j runtime (D-046)

`LangGraphWorkflowEngine` is now the active and only orchestration engine.
It registers the 18 business nodes with LangGraph4j 1.8.25, conditional edges,
a MemorySaver and an interrupt after APPROVAL_INTERRUPT. Resume invokes the
checkpointed graph; after restart, durable business approval state re-enters
at revalidation. Investigator workers run four-node compiled LangGraph4j loops
with isolated invocation state and a shared deterministic tool budget.

`GET /api/v1/workflows/graph` returns the actual compiled Mermaid diagram.
Node/model/tool transitions feed the Trust UI, business audit and OTLP traces.
See `docs/try1-setup.md` for Sarvam and Langfuse configuration and persistence limits.

## Historical spike decision — superseded by explicit user instruction D-046

Spike executed in isolation against `org.bsc.langgraph4j:langgraph4j-core:1.8.26` (latest 1.8.x on Maven
Central; 1.9.0-beta4 exists and is excluded by D-028). Five gates, one JUnit test each, all passing:

| Gate | Result | Note |
|---|---|---|
| Conditional routing | pass | `addConditionalEdges` with `AsyncCommandAction` / `Command(gotoNode)` |
| Parallel fan-out / fan-in | pass with caveat | Two edges from `plan` produce a `__PARALLEL__(plan)` node and an `appender` channel merges both branches. Branches ran serially on the calling thread unless `RunnableConfig.addParallelNodeExecutor(node, executor)` is configured per node. |
| Checkpointed approval pause / resume | pass | `CompileConfig.interruptBefore("execute")` + `MemorySaver`; `getState(config).next()` is `execute`; `updateState` then `invoke(null, config)` resumed and executed exactly once; state history retained. |
| State serialization | pass | `ObjectStreamStateSerializer` round trip; Jackson/Gson serializers also shipped. |
| Nested tracing hooks | pass with caveat | `stream()` yields per-node `NodeOutput` (`__START__`, nodes, `__PARALLEL__`, `__END__`), enough to open one span per node. No built-in OpenTelemetry integration; spans must be wrapped around node actions. |

Verdict: **DROP for the hackathon build (deferred, not rejected).** The deterministic
`DeterministicWorkflowEngine` already delivers the 18-node routing, bounded fan-out with real
parallelism, checkpointed approval pause/resume, one correction cycle and typed transition events
with full test coverage. Adopting LangGraph4j now would add a root dependency (Integration Owner
change), a second implementation of the same `WorkflowEngine` contract to keep green, and no
judge-visible capability. Reconsider after the demo if a graph visualiser or LangGraph Studio-style
inspection becomes a requirement; the spike code lives outside the repository and the node contracts
here are framework-neutral, so the adapter can be added without touching agents, tools or policy.
