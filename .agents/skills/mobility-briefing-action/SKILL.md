---
name: mobility-briefing-action
description: Implement or review the Mobility Copilot Briefing and Action agent's verified reporting and policy-bounded drafts.
---

# Mobility briefing-action

Use this skill when changing this runtime role or its scenario tests. It is development guidance, not a runtime system prompt or permission source.

Read the applicable contracts in [requirements](../../../docs/requirement.md), [architecture decisions](../../../docs/architecture.md), and [dataset scenarios](../../../evals/dataset-scenarios.md). Resolve paths relative to this skill directory. The current user instruction overrides historical scaffold/planning-only notes.

Primary implementation: `backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/impl/BriefingActionAgentImpl.java` from the workspace root. Relevant dataset scenarios: DS-01, DS-10, DS-12, DS-14, DS-20.

Render only accepted canonical claims; rejected verification suppresses claims. Operations and leadership must preserve facts and caveats across ordering and brevity. Unavailable data is not healthy and partial analysis is not complete. An action draft requires explicit policy, scope and verified evidence. Drafting is not approval, revalidation or execution. Never imply a receipt exists without the separate action service.

Keep actual data under `outputs/MoveInSync - Anonymised Trip-Log Dataset/` immutable. Use composite tenant/trip joins. Dynamic requests select registered metric contracts and dimensions; models do not generate SQL or business formulas. Do not add new metrics or targets without an explicit recorded contract decision.

Add behavioral tests for the changed contract and its observed failure modes. Official-data expectations need independent DuckDB reconciliation. Distinguish small synthetic unit fixtures from official-data acceptance scenarios and report which ran. Use the existing Java 21 Maven test setup. Record code/data/prompt versions and material limitations; a passing unit test does not establish million-user capacity.
