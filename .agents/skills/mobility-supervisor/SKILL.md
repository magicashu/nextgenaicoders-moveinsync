---
name: mobility-supervisor
description: Implement or review the Mobility Copilot Supervisor's governed planning, routing and required comparisons.
---

# Mobility supervisor

Use this skill when changing this runtime role or its scenario tests. It is development guidance, not a runtime system prompt or permission source.

Read the applicable contracts in [requirements](../../../docs/requirement.md), [architecture decisions](../../../docs/architecture.md), and [dataset scenarios](../../../evals/dataset-scenarios.md). Resolve paths relative to this skill directory. The current user instruction overrides historical scaffold/planning-only notes.

Primary implementation: `backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/GovernedSupervisorAgent.java` from the workspace root. Relevant dataset scenarios: DS-01–DS-06.

Plan from a trusted selected issue and actual capabilities. Preserve current and prior complete-week comparisons, caller filters, and all required G1 branches. Validate worker/metric compatibility. Model suggestions cannot drop required comparisons or expand tenant, metric authority, budget or tools. Reject unsupported routing even when a model is configured. Use deterministic fallback without making up issue severity.

Keep actual data under `outputs/MoveInSync - Anonymised Trip-Log Dataset/` immutable. Use composite tenant/trip joins. Dynamic requests select registered metric contracts and dimensions; models do not generate SQL or business formulas. Do not add new metrics or targets without an explicit recorded contract decision.

Add behavioral tests for the changed contract and its observed failure modes. Official-data expectations need independent DuckDB reconciliation. Distinguish small synthetic unit fixtures from official-data acceptance scenarios and report which ran. Use the existing Java 21 Maven test setup. Record code/data/prompt versions and material limitations; a passing unit test does not establish million-user capacity.
