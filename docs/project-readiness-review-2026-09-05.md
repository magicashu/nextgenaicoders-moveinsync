# Project readiness review — 5 September 2026

The project is a strong, tested decision-support prototype with unusually careful data handling. It is not yet as convincing on proactive AI behavior as it is on deterministic engineering. My assessment is **strong foundation, credible hackathon demo, meaningful room to improve the winning story**. This is a qualitative review, not an official judge score or a claim about competing teams.

The highest-return next steps are a working bounded model adapter, proactive replay of the supplied data, and a leadership-ready output that makes the business decision immediately clear.

## What I verified

Reviewed the required project context and decision register, the existing four rendered problem-statement pages, the official-data capability and metric contracts, runtime implementation, frontend flow code, and saved evaluation artifacts on `Java-branch-2`.

Ran `./scripts/verify.sh` successfully: **127 backend tests, seven frontend interaction tests, and a successful production build**. This included official-dataset DuckDB gates, reconciliation, and the composed approval/resume workflow. The PostgreSQL integration test class executed zero tests; this run does not establish durable PostgreSQL recovery. Frontend interaction tests use typed fixtures. I did not perform a fresh browser walkthrough or rerun the entire HTTP release rehearsal. Existing HTTP scorecard results are historical evidence, not a new live measurement.

## Alignment with the actual problem

| Requirement or judging area | Current coverage | Assessment |
|---|---|---|
| Runs on supplied data; functionality worth 25% | Official CSV ingestion, DuckDB queries, reconciliation and composed workflow tests pass | Strong |
| Serves a named persona | Operational and leadership briefs, investigation, approval and audit views | Strong scope alignment; leadership usability still needs a human review |
| Contextual metrics | Historical baselines, configured targets and vendor/site comparisons | Strong; configured targets must remain visibly distinguished from organizer SLAs |
| Senses, reasons and acts | Multi-metric detection, bounded investigation, critic, approval and mock execution | Substantial workflow coverage; default runtime uses deterministic role fallbacks |
| Business impact and experience, worth 35% | Impact estimates, evidence, recommended actions and dual briefs | Promising; demonstrate reduced manager effort and a forwardable output |
| Agentic design and cost at scale, worth 20% | Model port, versioned prompts, bounded calls and local traces | Biggest gap: no concrete production model adapter found; no scheduler found; cost evidence incomplete |
| Architecture and code quality, worth 20% | Typed Java ports, governed SQL, React, tenant checks, tests and release scripts | Strong local foundation; optional infrastructure needs separate verification |
| Messy/missing data | Capability gates, caveats, dedupe, unsupported metrics and corrupted variants | A major strength |

Real vendor integrations and production-grade authentication are explicitly outside the required scope. Approved mock actions are appropriate for this challenge. React is also acceptable: the statement prefers Java/Angular/AWS but does not mandate them.

## How well the actual data is used

The documented intake covers seven official CSV files across five business units and May–July 2026. The fresh official-data tests exercise these files through DuckDB. The project goes beyond importing tables: it defines M01–M18, supported denominators, exclusions, tenant capabilities and golden cases.

Fresh reconciliation reproduced, among other checks:

- G1 delayed-trip rate: **4,357 / 19,913 = 21.88%**, against **9,186 / 74,814 = 12.28%** in the baseline.
- **6,753** trip IDs collide between tenants, making `(business_unit, trip_id)` essential.
- **708** duplicate employee-leg rows and **72** exact duplicate bill rows are removed.
- Cost per kilometre is explicitly unsupported where distance is unusable.

The detector actively scans eight metrics: severe alerts, delayed trips, pickup punctuality, drop punctuality, no-shows, low driver ratings, tracking gaps and overall alerts. Cost, occupancy and EV share are not in that sensed-metric list, even though the governed metric layer offers broader coverage. Availability of a metric is therefore not the same as proactive product coverage.

The three golden stories are well chosen: a site/shift delay issue that avoids unjustified vendor blame; deteriorating service with data caveats; and an alert configuration change that must not become a false operational escalation. Showing the third case is particularly valuable because it demonstrates judgment about when not to act.

Respect the dataset boundaries: it has no GPS coordinates, driver IDs, route IDs, budget targets or free-text feedback. Use site × shift × direction as the documented route proxy. Sparse feedback limits representativeness. Do not add maps, driver-level blame, budget savings, text sentiment or carbon-reduction claims without the missing evidence.

## Most important gaps

1. **A real AI contribution is not yet demonstrated.** `WorkflowCompositionConfiguration` binds `LanguageModelPort.Unavailable` by default; no concrete provider implementation was found. Adding the optional OpenAI dependency alone does not connect that port. The fallback is valuable, but judges should also see a bounded model improve investigation or explanation.
2. **Sensing remains request-driven in the inspected implementation.** No scheduler or scheduled trigger was found. A morning-brief endpoint does not itself prove that the product surfaces issues without a user request.
3. **Conversation is constrained.** `ContextualQuestionService` classifies questions through keyword lists. A related run is checked for access, then a new question workflow is launched; the inspected gateway does not carry that prior run's evidence forward as conversational state. Test unscripted follow-ups before presenting it as rich conversational investigation.
4. **Cost and latency evidence needs tightening.** `scripts/demo/scorecard.sh` hardcodes `estimatedCostUsd` to zero. Model usage includes fallback entries. The UI does expose fallback counts, which helps, but a count of those entries is not a count of actual provider calls. Saved latency values are not a representative p95 benchmark or proof of AI inference performance.
5. **The release story needs product proof.** Passing tests do not establish that a transport head can forward the brief without editing, that a new user can navigate the live UI, or that restarting the default in-memory runtime preserves an approval.

## Additions, in priority order

| Priority | Addition | Concrete acceptance proof |
|---|---|---|
| 1 | Implement one real bounded `LanguageModelPort` adapter | A live run shows validated model planning or synthesis, actual token usage, and identical governed facts; a forced timeout produces the deterministic fallback |
| 2 | Add proactive replay over the historical dataset | Advancing a replay date triggers sensing without asking a question; a material issue appears once; a healthy window does not create noise; external actions still require approval |
| 3 | Make the leadership brief directly shareable | Copy/export one page containing what changed, who is affected, benchmark, caveats, proposed action and evidence links; no manual rewriting |
| 4 | Correct observability accounting | Distinguish model attempts, actual completions and fallbacks; derive cost from measured usage and configured rates; separately report cold/warm and model/fallback latency over repeated runs |
| 5 | Add one business breadth story | Use existing governed cost-per-trip and vendor comparisons, or a supported safety investigation; show a review opportunity rather than promising savings or asserting causality |
| 6 | Close the watchlist loop | Attach owner, due date and a later-window recheck to the approved mock watchlist; report observed change without claiming the intervention caused it |

After those, useful optional additions are an evidence-grounded explanation of why an issue outranked others, a tenant capability panel, and EV-share/occupancy trends using existing contracts. A third persona can wait: the challenge requires one, and the current two are a sensible scope.

These are recommendations only. They do not amend accepted architecture or authorize implementation. Record chosen scope and any new metric/threshold contracts in the decision register before implementing them.

## Recommended demonstration

Start with a replay tick producing a prioritized morning issue. Open the benchmark and affected population, investigate site/shift versus vendor explanations, and show the evidence critic rejecting unsupported blame. Switch to the leadership brief, approve a clearly labelled mock watchlist, and inspect the receipt/audit. Then show the degraded-data and false-alert cases. Finish with a real model trace, fallback proof and accurately labelled cost/latency measurements.

Spend the next effort on making that sequence reliable and persuasive. Additional frameworks, RAG, production authentication and real vendor connectors offer less value for the stated rubric than completing the proactive AI and business-output story.

## Evidence locations

- `references/problem_explanation_7qdzf3jxklt.pdf` and existing renders under `tmp/pdfs/problem_statement/`
- `docs/live-problem-statement-analysis.md`
- `docs/dataset-profile-and-capability-matrix.md`
- `docs/hackathon-decision-register.md`, especially D-042–D-044
- `backend/src/main/java/com/moveinsync/mobilitycopilot/config/WorkflowCompositionConfiguration.java`
- `backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/ModelAssist.java`
- `backend/src/main/java/com/moveinsync/mobilitycopilot/anomaly/application/DeterministicAnomalyDetectionService.java`
- `backend/src/main/java/com/moveinsync/mobilitycopilot/conversation/application/ContextualQuestionService.java`
- `backend/src/main/java/com/moveinsync/mobilitycopilot/reporting/adapter/WorkflowDecisionRunGateway.java`
- `scripts/demo/scorecard.sh`, `evals/results/metric-results.json`, `evals/results/scorecard.json`
- Fresh validation command: `./scripts/verify.sh`
