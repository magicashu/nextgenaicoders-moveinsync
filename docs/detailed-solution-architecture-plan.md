# Mobility Decision Copilot — detailed workflow design

This is a behavior contract for a separate team build. It uses responsibility names, not reference source filenames or implementation class names. Read the [complete plain-English handbook](team-handbook/plain-english-build-guide.md) for context and architecture components.

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


## Shared records and build acceptance

Agree the request, job, run, evidence, plan, claim, brief, proposal, decision, receipt and audit records described in the handbook before component development. Each producer and consumer must agree error cases as well as success cases.

Use the [work packages](team-handbook/team-work-packages.md) to assign owners. Use the [data and metric rules](team-handbook/data-and-metric-rules.md) for calculations. A new build must independently demonstrate its golden, security, failure, recovery and UI acceptance checks.
