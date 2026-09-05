# Selected build decisions

These choices are based on the official problem statement and supplied structured dataset. They define the team build; they do not claim measured performance or completed implementation.

## What we are building

A manager receives a prioritized mobility issue, investigates its evidence, reviews a recommendation, approves a mock action and forwards a leadership brief. The same verified facts support every view.

| Component | Choice | Why it fits |
|---|---|---|
| Application backend | One Java 21/Spring Boot application | Keeps deployment simple while allowing clear component ownership. |
| Browser experience | React and TypeScript | Supports the accepted team design and accessible, responsive evidence views. |
| Workflow | Explicit Java state machine | Makes transitions, limits, failures and approval resume visible and deterministic. |
| AI roles | Supervisor, Investigator, Evidence Critic, Briefing/Action | Separates planning, investigation, challenge and explanation without an agent for every team member. |
| Analytical workers | Seven governed domain tools | Reuses the same bounded investigation pattern across the available data. |
| Analytics | DuckDB | Fits the structured batch dataset and governed aggregate queries. |
| Durable control state | PostgreSQL | Stores shared jobs, approvals, checkpoints, duplicate prevention and audit. Local in-memory mode is a clearly labeled fallback. |
| Model assistance | Optional server-side Sarvam adapter | Explains compact verified evidence and proposes bounded work; deterministic operation remains available. |
| Retrieval | No mandatory document RAG | The current files do not supply a decision-relevant policy or playbook corpus. |
| Delivery | Complete batch golden path first | Proves a useful decision and controlled action before optional replay or infrastructure. |

Spring AI or a graph library is not a prerequisite. A future framework or storage change needs a concrete benefit, compatible contracts and measured validation. More agents, a larger model or a different database do not by themselves prove lower latency.

## Data rules that cannot drift between teammates

The [approved data and metric rules](data-and-metric-rules.md) are the numerical authority.

- Use the pair (business_unit, trip_id) for trip identity and all joins.
- Keep trip delayed-rate M01 separate from employee pickup/drop M04/M05. Exploratory five-minute OTA does not replace them.
- Retain approved occupancy caps, billing exclusions and median, no-show eligibility, severe-alert acknowledgement and outlier rules.
- Keep data versions, metric versions, evidence, source quality reasons and capability limits visible.
- Treat missing or unsupported facts as unavailable, not zero.
- Use only supported dimensions; route is a site/shift/direction proxy. Do not invent GPS positions, budgets, emissions savings or proven causal effects.

## Ideas adopted from the new proposals

Use requirement IDs and acceptance evidence for each work package. Show a prioritized exception queue, meaningful comparisons, supported severity/category filters, actual workflow progress, an evidence drawer and approval details. Produce a concise leadership brief from the same verified facts.

Complete shared records before independent implementation: actor/scope, run and data versions, typed status/errors, claim-to-evidence mappings, exact proposal versions, approval decisions and receipts. An edited proposal requires renewed validation. A mock execution receipt does not mean an operational incident was resolved.

Preserve keyboard access, readable contrast, text alternatives, visible focus and reduced-motion behavior. UI examples must not introduce uncomputed scores, confidence percentages or unsupported metrics.

## What comes later

Replay is optional and must be labeled simulation, with consistent data cutoffs and a working batch fallback. Document retrieval needs an authorized corpus and document-dependent evaluation cases. Analytical publication/rollback, a persistent row-level quality ledger and incident follow-up are separate extensions.

Use bounded job queues, tool execution, provider concurrency and version-aware caching from the start. Measure cold/warm latency, queue wait, fresh completion, failures and memory under a named workload; report provider completion separately from fallback. Performance goals remain experiments until measured.

## Team handoff

Use the [work packages](team-work-packages.md), combining responsibilities for a smaller team while keeping one designated integration owner. Follow the [plain-English component guide](plain-english-build-guide.md) for all four agents, eighteen main nodes, four investigation stages and seven workers.

G1 must find the site/shift issue and reject unsupported single-vendor blame. G2 must retain its coverage and unavailable-metric caveats. G3 must become a data-quality note without operational escalation. Complete approval, fresh revalidation, duplicate protection and audit before calling the golden path finished.
