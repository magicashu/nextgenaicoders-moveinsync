# Mobility Decision Copilot — plain-English build guide

## 1. What we are building

The system helps a transport manager answer four questions: What changed? How important is it? What evidence explains it? What should we review or do next?

It reads the supplied historical transport data, compares approved measurements, investigates a meaningful change, and produces an operations brief and a leadership summary. If an action is appropriate, it asks a person to approve it. Demo actions are simulated and must be labelled clearly.

This guide defines behavior for a separate team build. It does not depend on a reference implementation or describe work as already completed.

## 2. Agent, node, tool and component: four different things

- An **agent** is a role where AI can help choose an investigation or organize an explanation. We have four roles.
- A **node** is one step in the workflow. Most nodes use fixed rules, not AI. We have 18 main steps.
- A **tool** performs one approved data analysis and returns checked results. AI cannot invent a new tool or database query.
- An **architecture component** is a larger part of the product, such as the dashboard, data loader, approval system or queue.

The four AI roles do not require four servers or four people. The team can build them within one controlled application. A human developer's assignment is different from an AI agent's runtime responsibility.

## 3. The journey, from request to outcome

1. A user asks for a brief, or a configured schedule requests one.
2. The system checks who is asking and which business unit they may access.
3. It reuses a recent matching result or puts the request in a bounded background queue.
4. It checks available data, calculates approved measurements and chooses a material issue.
5. The supervisor plans the investigation. The investigator runs approved analyses.
6. Evidence is combined, challenged by the critic and checked by fixed rules.
7. The briefing role prepares two audience views using the same checked facts.
8. The policy gate decides whether an action can be offered for approval.
9. A human approves, edits or rejects the proposal.
10. Before any simulated action, the system checks permission, evidence and proposal again.
11. The system records what happened and shows the outcome.

A healthy result does not need an action. A failed data check does not mean operations are healthy. A completed brief may still be waiting for approval.

## 4. The four agents

### Agent 1 — Supervisor and planner

**Job:** Decide which approved investigations can explain the selected issue.

**Receives:** The issue, user role, business unit, available data, allowed analyses and remaining work allowance.

**Does:** Selects relevant investigation tasks. For example, a delay increase may need vendor and site/shift comparisons. Essential broad comparisons must survive even if AI suggests a narrower plan.

**Produces:** A short plan containing tasks, scope, required evidence and stopping conditions.

**Must not:** Calculate metrics, change tenant, invent thresholds, run SQL or approve actions.

**If AI fails:** Use a fixed plan appropriate to the issue and available data.

**Ready when:** The same input can produce a valid plan without AI, and invalid workers or foreign-tenant filters are rejected.

### Agent 2 — Investigator

**Job:** Gather enough evidence to answer one assigned question.

**Receives:** One approved task, allowed tools, the current and comparison periods, and a work allowance.

**Does:** Chooses a registered analysis, checks its result, and requests a further allowed analysis only when evidence and remaining budget justify it. Several task investigations may run together.

**Produces:** Findings linked to evidence, supporting populations, warnings, uncertainties and a complete/partial/failed status.

**Must not:** Query unrestricted data, calculate new business formulas, take actions or continue indefinitely.

**If a tool fails:** Preserve useful completed evidence, identify what is missing and stop when the allowance expires.

**Ready when:** A slow or failed branch cannot hang the whole run, and evidence from the wrong business unit, date range or data version is rejected.

### Agent 3 — Evidence critic

**Job:** Challenge the conclusions before the user sees them.

**Receives:** Claims, supporting evidence, data limitations and the rules for acceptable claims.

**Does:** Questions unsupported attribution, contradictions and missing caveats. It may point to an existing claim or known limitation for review.

**Produces:** A structured review: acceptable, needs correction, or insufficient evidence.

**Must not:** Invent missing facts, fetch extra data or overrule the final fixed-rule verifier.

**If AI fails:** Use deterministic evidence checks and known caveats.

**Ready when:** An unsupported statement such as blaming one vendor when all qualified vendors worsened cannot pass unchanged.

### Agent 4 — Briefing and action drafting

**Job:** Turn the checked evidence into a useful decision brief.

**Receives:** Verified claims, audience, all material caveats and the allowed action policies.

**Does:** Organizes the same facts into an operations view and a leadership view. AI may select or order verified claim identifiers; fixed templates render their approved wording. Fixed rules choose and scope any action proposal.

**Produces:** Two consistent summaries and, when appropriate, a draft proposal with evidence, scope and expiry.

**Must not:** Add numbers, omit important caveats, silently change meaning, approve an action or contact a vendor.

**If AI fails:** Use the deterministic report template.

**Ready when:** Both audience views remain faithful to the same evidence and every proposed action remains a draft.

## 5. The 18 workflow nodes, one by one

The labels are responsibilities, not required source-file or class names.

| # | Node | Receives | What it does in simple terms | Produces / next step | If it cannot proceed |
|---:|---|---|---|---|---|
| 1 | Start the run | Request, identity and settings | Gives this attempt an ID and records its date, versions and work limits | Run record → 2 | Reject invalid input |
| 2 | Check access | Identity, role and requested business unit | Confirms that this person may request these analyses | Authorized scope → 3 | Deny before data access |
| 3 | Check the dataset | Authorized scope and data catalog | Reads availability and quality information for the chosen data version | Data profile → 4 | Record missing/invalid sources |
| 4 | Decide available capabilities | Data profile and metric requirements | Determines which analyses are supported, limited or unavailable | Capability list → 5 | Disable affected analyses; retain safe ones |
| 5 | Calculate the metric snapshot | Scope, date and approved definitions | Loads or computes current values, baselines, populations and units | Versioned measurements → 6 | Explain unavailable measurements; never substitute guesses |
| 6 | Detect meaningful changes | Measurements and approved detection rules | Separates material operational changes, healthy results and data-regime changes | Candidates → 7; healthy brief → 18 | No usable metrics means unavailable, not healthy |
| 7 | Choose the issue to investigate | Valid candidates and priority rules | Picks the highest-priority issue using the approved rules | Selected issue → 8 | Provide a qualified outcome if no eligible issue remains |
| 8 | Plan the investigation | Issue, capabilities and budget | Runs the supervisor role | Draft investigation plan → 9 | Use a deterministic plan |
| 9 | Validate the plan | Plan and authorized scope | Checks worker names, filters, data support and budget | Allowed task list → 10 | Remove invalid tasks; stop if none remain |
| 10 | Run investigations | Valid tasks | Runs the investigator for each task within shared resource limits | Task results → 11 | Preserve complete/partial branches and show failures |
| 11 | Merge the evidence | Task results and selected-issue evidence | Removes duplicates, links claims and retains quality/coverage warnings | One evidence package → 12 | Mark gaps and inconsistent evidence |
| 12 | Critique the evidence | Claims and evidence package | Runs the evidence critic | Review result → 13 | Continue with deterministic checks |
| 13 | Verify the claims | Evidence, claims and critic feedback | Checks cited evidence, numbers, scope, versions and allowed meaning | Verified or qualified claims → 14 | At most one claim-removal/recheck cycle; failed verification cannot authorize an action |
| 14 | Prepare the decision brief | Checked claims and allowed action policies | Runs the briefing role and fixed templates | Operations brief, leadership brief, optional draft → 15 | Use a safe template and show limitations |
| 15 | Check action policy | Proposal and verification result | Checks action type, business-unit scope, evidence, confidence and expiry | Eligible proposal → 16; report-only → 18 | Block the proposal while preserving a qualified report |
| 16 | Wait for human approval | Eligible proposal | Saves the pending decision and lets the user approve, edit or reject | Durable pause; human decision → 17 | Keep pending, or expose persistence/expiry problems |
| 17 | Recheck and perform the simulated action | Saved proposal and human decision | Handles rejection; for approval/edit, rechecks access, expiry, fresh evidence and full proposal before one simulated effect | Receipt or non-execution reason → 18 | Never execute on stale evidence, invalid edits or failed checks |
| 18 | Record and finish | Run outcome and relevant events | Records the final business outcome, references and receipt | Readable terminal result | Surface audit/persistence failures; do not silently claim success |

The ordinary path is 1 → 2 → … → 16, then 17 → 18 after a person responds. Healthy and report-only paths end earlier. Access denial stops before analytics.

The correction at node 13 only removes unsupported claims and checks again. It does not restart planning or repeatedly call tools.

## 6. The investigator's four smaller steps

Each task at node 10 uses the same small loop.

| Step | Plain-English responsibility | Stop rule |
|---|---|---|
| Choose an analysis | Select a registered tool relevant to this task and available evidence | Stop when no allowed analysis can help |
| Run the analysis | Call it with checked tenant, date, dimension and filter values | Respect the call/time allowance |
| Validate the result | Check business unit, time window, data version, measurement definitions and quality | Reject mismatched or malformed evidence |
| Check progress | Decide whether enough has been learned or one bounded follow-up is justified | Finish as complete, partial or failed when evidence or budget runs out |

Follow-up scope must be justified by the evidence. AI cannot narrow the investigation arbitrarily to manufacture a preferred conclusion.

## 7. Seven analysis workers

A worker is an approved analytical job, not another permanent AI agent.

| Worker | Question it helps answer | Important limitation |
|---|---|---|
| Vendor | Did one vendor worsen, or did the change affect vendors broadly? | Universal vendor claims require qualification in both periods |
| Site, shift and direction | Where and when is the change concentrated? | These are analytical groupings; do not invent a route ID |
| Delay reason | Which recorded reason categories contribute to delays? | A reason category is not proof of root cause |
| Cost and billing | What do the approved billed-cost measures show? | No budget/savings claim without data; cost-per-km may be unsupported |
| Feedback | What do ratings show and how much coverage do they have? | Low coverage must remain visible; follow the rating-zero policy |
| Tracking and safety alerts | Are valid alert/tracking measures changing? | No GPS map; exclude known recording-regime changes from operational escalation |
| No-show and roster | What do eligible employee-leg records show about no-shows and related measures? | Use the approved leg population, not a guessed trip denominator |

## 8. Every main architecture component

| Component | What it does | What it hands to others | Suggested work package |
|---|---|---|---|
| Dashboard | Shows briefs, evidence, questions, approval, audit and trust readings | Scoped user requests and decisions | 5 |
| API boundary | Validates requests and connects screens to services | Consistent responses and safe errors | 4 |
| Identity and access | Determines who may see or approve what | Authorized business-unit and role scope | 3 |
| Request queue | Accepts bounded background work and reports progress | Job identifier, status and result reference | 4 |
| Scheduler | Requests precomputation for configured historical dates and tenants | The same scoped work requests used by the UI | 4 |
| Data loader and normalizer | Reads untouched files and handles approved formatting differences | Consistent data with provenance | 1 |
| Data-quality profiler | Finds missing, invalid, duplicate and unmatched records | Quality report and exclusions | 1 |
| Capability checker | Matches data availability to analysis requirements | Supported/limited/unavailable flags | 1 |
| Metric registry | Defines each approved measurement and its denominator, unit and exclusions | Versioned definitions | 1, agreed with integration owner |
| Analytical store and query service | Executes the governed calculations | Checked metric and contribution evidence | 1 |
| Metric cache | Reuses recent calculations for identical authorized scope and versions | The same result with less repeated work | 1 |
| Detector and prioritizer | Applies approved change/materiality/priority rules | Selected issue and its context | 1 |
| Workflow controller | Coordinates the 18 steps, errors and budgets | A traceable run and terminal/pending status | 2 |
| AI provider adapter | Sends bounded aggregate prompts to optional Sarvam assistance | Structured suggestions and usage records | 2 |
| Evidence merger and verifier | Combines results and enforces claim rules | Verified claims plus limitations | 2 |
| Report renderer | Presents checked facts for two audiences | Briefs, answers and shareable text | 4 |
| Action policy and approval | Decides whether a proposal can be shown for a human decision | Pending/approved/edited/rejected decision | 3 |
| Revalidation and action executor | Rechecks approved details and prevents duplicate simulated effects | Execution receipt or reason nothing happened | 3 |
| Durable control store | Saves jobs, progress, pending decisions, evidence snapshots and receipts | Restart-readable control records | 3; queue behavior owned by 4 |
| Business audit ledger | Records consequential business events | Who decided what, with which evidence and outcome | 3 |
| Observability and evaluation | Measures durations, tool/model use and failures; checks release behavior | Diagnostics and acceptance evidence | 6 |

Recommended technology choices can remain Java/Spring Boot, React/TypeScript, DuckDB analytics, PostgreSQL shared control state, optional Sarvam and OpenTelemetry/Langfuse. These choices do not change the responsibilities above.

## 9. What the components must exchange

Agree these records before building screens or independent components. Each record needs required/optional fields and error behavior. Use business names, then choose implementation names together.

| Record | Minimum meaning |
|---|---|
| Request | Who asked, business unit, role, as-of date, request purpose and allowed filters |
| Job status | Job ID, queued/running/complete/failed, timestamps, result reference and safe error |
| Run | Run ID, scope, current step, versions, work allowance, errors and outcome |
| Metric evidence | Metric ID/version, business unit, period, filters, value/unit, population, numerator/denominator when applicable, source and quality |
| Investigation plan | Selected issue, allowed tasks, scope, required evidence and stop conditions |
| Task result | Worker, complete/partial/failed status, evidence, findings and limitations |
| Claim | Claim ID, exact text, kind and supporting evidence IDs |
| Brief | Checked claims, operations/leadership presentations, caveats and optional proposal |
| Proposal | Action ID, type, run, tenant scope, exact proposed details, evidence version and expiry |
| Human decision | Proposal/approval identity, approver, approve/edit/reject, time and approved details |
| Receipt | Action identity, duplicate-prevention key, simulated outcome, time and audit reference |
| Audit event | Tenant/run/action references, actor, event type, time and outcome |

A data version identifies an analytical dataset. A run ID identifies one analysis attempt. An action ID identifies one proposal/effect. Keep them distinct.

## 10. Rules everybody must preserve

- Treat business_unit as the tenant. Join and aggregate trips using both business_unit and trip_id.
- Use approved M01–M18 definitions and quality rules. The normal historical comparison uses the prior four complete weeks.
- Source product_type maps to mode; shift_type to shift_id; office to site_id; trip_direction to direction.
- Never modify supplied files. Generate bad-data test variants separately.
- A cache hit must match identity where relevant, tenant, filters, time period, data version and answer-affecting settings.
- AI receives bounded aggregate evidence and may choose among approved options. It cannot create operational facts or grant permission.
- Missing data, failed checks and low coverage must be visible. They are not healthy results.
- A draft is not approval. Approval is not execution. Simulated execution is not incident resolution.
- Record an audit event for consequential actions. Repeated approval requests must not cause repeated effects.
- Keep shared mutable control records in PostgreSQL. Do not have a reader process and writer process independently open the same active native DuckDB control file.
- Timeouts, loop limits, queue capacity and provider concurrency must be explicit. Increasing one limit is not proof of scalability.
- Provider cost is an estimate only when rates and usage are known. Unknown cost is not zero.
- Data and authorization take priority over a fluent explanation.

## 11. Initial operating settings

These are design starting points, not achieved performance claims or anomaly thresholds.

| Setting | Starting point |
|---|---|
| Background brief workers | 2; bounded queue, maximum 256 jobs |
| Investigation execution | 4 workers; queue capacity 64 |
| Per-run analytical calls | Maximum 12 |
| Investigation depth | Maximum 4 steps |
| Claim correction | Maximum 1 cycle |
| Tool timeout | 10 seconds |
| Metric cache | Up to 2,048 entries, ten-minute expiry |
| Capability cache | Up to 128 entries, ten-minute expiry |
| Matching brief reuse | Ten minutes, with authorized identity and version scope |
| Optional provider concurrency | 2 calls per process |
| Approval lifetime | 30 minutes, with expiry rechecked before execution |
| Precomputation | Off until historical date, tenants and request identity are explicitly configured |

A provider can be absent. Every role still needs a deterministic path. Restart tests must distinguish recovering a saved completed/pending run from continuing halfway through an interrupted computation.

## 12. Scope for the first delivery

Build one complete journey: request → checked metrics → investigation → verified brief → human approval → one audited simulated effect. Then add degraded-data and security cases.

Use the named golden cases from the approved data contract: G1 for the primary delay investigation, G2 for limited-data conclusions and G3 for a recording change that must not be escalated as an operational incident.

Later additions need their own acceptance checks:
- Immutable dataset publication and keeping the last-good dataset after a failed refresh.
- Detailed, permission-filtered source-row quality reports.
- Persistent incidents with acknowledgement, reopening and outcome review.
- A separate ingestion service or Parquet conversion when measurements justify it.

Do not add document retrieval, predictive claims or live vendor integration merely to increase feature count.

## 13. How to divide the work

Read [team work packages](team-work-packages.md) for ownership, dependencies, handoff records and acceptance checks. Read [data and metric rules](data-and-metric-rules.md) before implementing any measurement.
