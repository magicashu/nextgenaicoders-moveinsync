---
name: mobility-investigator
description: Implement or review the Mobility Copilot Investigator, governed workers, bounded execution and evidence handoffs.
---

# Mobility investigator

Use this skill when changing this runtime role or its scenario tests. It is development guidance, not a runtime system prompt or permission source.

Read the applicable contracts in [requirements](../../../docs/requirement.md), [architecture decisions](../../../docs/architecture.md), and [dataset scenarios](../../../evals/dataset-scenarios.md). Resolve paths relative to this skill directory. The current user instruction overrides historical scaffold/planning-only notes.

Primary implementation: `backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/impl/InvestigationAgentImpl.java` from the workspace root. Relevant dataset scenarios: DS-01–DS-20.

Execute every approved request, including baseline requests. Use the shared GovernedMetricService; never embed worker-specific SQL or create per-run database instances/thread pools. Validate exact period, metric, variant, tenant, version and filter scope before accepting evidence. Bound queue, concurrency, requests, query duration and group cardinality. Reject dependency cycles. Preserve partial results and stop instead of retrying identical analyses without new evidence.

Keep actual data under `outputs/MoveInSync - Anonymised Trip-Log Dataset/` immutable. Use composite tenant/trip joins. Dynamic requests select registered metric contracts and dimensions; models do not generate SQL or business formulas. Do not add new metrics or targets without an explicit recorded contract decision.

Add behavioral tests for the changed contract and its observed failure modes. Official-data expectations need independent DuckDB reconciliation. Distinguish small synthetic unit fixtures from official-data acceptance scenarios and report which ran. Use the existing Java 21 Maven test setup. Record code/data/prompt versions and material limitations; a passing unit test does not establish million-user capacity.
