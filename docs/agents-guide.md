# Agents and responsibilities

This file explains the four runtime agent roles. It describes what individual team members must build; the current Java scaffold provides interfaces, not the role logic.

Use [requirement.md](requirement.md) for the official metrics and acceptance gates, [architecture.md](architecture.md) for packages and team ownership, and [Understanding the problem statement](<Understanding the problem statement.md>) for the full journey and node explanations.

## The four agents

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

## Shared rules and handoff

All roles receive the same trusted run scope, versions and shared work budget. The model cannot authorize a tenant, calculate a KPI, invent a threshold, run arbitrary SQL or execute an external action.

The Investigator uses seven governed workers: vendor; site/shift/direction; delay reason; cost/billing; feedback; tracking/safety; no-show/roster. Workers are reusable tools, not extra permanent agents. Each follows choose analysis → execute → validate result → progress check.

The Critic challenges interpretation; deterministic verification checks each claim's cited value, unit, population, period, tenant and version. The Briefing role may only organize verified facts. Policy, human approval, fresh revalidation, duplicate prevention and audit remain separate deterministic components.

Assign a person to each implementation task through architecture.md's ownership table. Do not equate one runtime agent with one required team member. Before handing over, record the role's input/output contract, permitted tools, budget/failure behavior, requirement IDs, tests and unfinished work.

The key acceptance stories are G1 (site/shift concentration and rejection of unsupported single-vendor blame), G2 (explicit coverage and unavailable-metric caveats) and G3 (data-quality note without operational escalation). The full definitions are in requirement.md.
