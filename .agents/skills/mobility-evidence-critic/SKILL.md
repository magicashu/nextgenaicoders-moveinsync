---
name: mobility-evidence-critic
description: Implement or review the Mobility Copilot Evidence Critic and deterministic claim verification.
---

# Mobility evidence-critic

Use this skill when changing this runtime role or its scenario tests. It is development guidance, not a runtime system prompt or permission source.

Read the applicable contracts in [requirements](../../../docs/requirement.md), [architecture decisions](../../../docs/architecture.md), and [dataset scenarios](../../../evals/dataset-scenarios.md). Resolve paths relative to this skill directory. The current user instruction overrides historical scaffold/planning-only notes.

Primary implementation: `backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/EvidenceCriticAgentImpl.java` from the workspace root. Relevant dataset scenarios: DS-03, DS-10, DS-12, DS-14–DS-19.

Verify canonical claim wording against cited values, units, eligible populations, dates, filters and pinned versions. Evidence presence is insufficient. Use per-metric versions inside the registry version. Review valid claims as positive controls as well as unsupported interpretations. Preserve data-quality and coverage caveats. Semantic critique may reject or qualify but cannot introduce verified facts. Never claim causality, universal vendor blame, GPS coverage, emissions or compliance from proxies.

Keep actual data under `outputs/MoveInSync - Anonymised Trip-Log Dataset/` immutable. Use composite tenant/trip joins. Dynamic requests select registered metric contracts and dimensions; models do not generate SQL or business formulas. Do not add new metrics or targets without an explicit recorded contract decision.

Add behavioral tests for the changed contract and its observed failure modes. Official-data expectations need independent DuckDB reconciliation. Distinguish small synthetic unit fixtures from official-data acceptance scenarios and report which ran. Use the existing Java 21 Maven test setup. Record code/data/prompt versions and material limitations; a passing unit test does not establish million-user capacity.
