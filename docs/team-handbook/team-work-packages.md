# Team work packages

## Assign work by responsibility

Use six work-package owners and one integration owner. Put an actual person's name against each slot before work begins. With fewer people, combine roles while keeping the responsibility boundaries. Do not appoint a different developer to each runtime AI role simply because there are four agents.

| Slot | Owner name | Responsible for |
|---|---|---|
| Integration owner | To assign | Shared records, interfaces, dependencies, integration and release |
| 1 — Data and metrics | To assign | Loading, quality, capabilities, governed metrics, caching, detection and contributions |
| 2 — Workflow and agents | To assign | 18-step orchestration, four agents, seven worker tools, evidence verification and optional provider |
| 3 — Access and actions | To assign | Permissions, durable control state, approval, revalidation, duplicate prevention and audit |
| 4 — API and reporting | To assign | Request/response boundary, async jobs, scheduling, questions and deterministic report rendering |
| 5 — User experience | To assign | Dashboard, charts, evidence, questions, approval, audit, trust and accessibility |
| 6 — Quality and observability | To assign | Acceptance evidence, negative cases, timing/usage traces, recovery and performance checks |

The integration owner freezes each interface with its producing and consuming owners. If an interface changes, update both sides and their examples together. Each person edits only their agreed scope until an explicit handoff.

## Package 1 — Data and metrics

**Input:** Untouched supplied files and approved data/metric rules.

**Build:** Normalize formats; record bad/missing/duplicate/unmatched rows; expose supported analyses; calculate current metrics and reference periods; identify material changes; return scoped contribution evidence. Add bounded caching without changing the answer.

**Output to package 2:** Data profile, capability list, metric snapshot, selected issue/candidates and typed worker results. Every analytical output carries tenant, period, filters, definition/version, units, population and quality.

**Owns nodes:** 3–7 analytical behavior. Package 2 controls their position in the workflow.

**Acceptance:** Reproduce the golden metric values; use tenant-qualified trip joins; reject unsupported dimensions; preserve low-coverage warnings; separate recording changes from operational anomalies; prove cached and uncached results agree.

## Package 2 — Workflow and agents

**Input:** Shared records, authorized scope, analytical outputs, control-store interfaces and provider settings.

**Build:** Node routing and termination, the four agents, four investigation stages and seven worker registrations. Bound calls/time/work; merge evidence; critique and verify claims; provide deterministic fallback when AI is absent or invalid.

**Output:** Investigation plan/results, checked claims, run transitions, proposal inputs, pending/terminal workflow status and provider usage.

**Owns nodes:** 1, 8–14 orchestration/agent behavior, and overall graph routing. Delegate access/action decisions to package 3.

**Acceptance:** Healthy, material, partial, denied, failed and approval paths all terminate correctly. An invalid plan cannot change tenant or remove essential comparisons. Unsupported model prose cannot become final facts. One correction does not restart an unbounded investigation.

## Package 3 — Access and actions

**Input:** Identity, permissions, proposal, checked evidence, human decision and durable storage interfaces.

**Build:** Tenant/role checks; pending approvals; expiry and allowed-edit rules; evidence/proposal revalidation; one-effect execution; receipts and append-only audit. Supply durable job/checkpoint/snapshot persistence interfaces agreed with packages 2 and 4.

**Output:** Authorization decision, pending approval, saved state, decision result, execution receipt or non-execution reason, audit history.

**Owns nodes:** 2 and 15–18 policy/action behavior. Package 2 calls these services.

**Acceptance:** Cross-tenant and unauthorized requests fail closed. Approval/edit is tied to the exact proposal. Stale evidence, expired proposals and rejected requests cannot execute. Repeated requests cause one simulated effect. Saved completed/pending runs can be read after restart. Interrupted work is never blindly replayed.

## Package 4 — API and reporting

**Input:** UI requests and domain/service interfaces.

**Build:** Validated transport requests, safe errors, async job submission/polling, optional precomputation, scoped questions and deterministic rendering of checked claims for operations and leadership. Authorize before reuse or lookup.

**Output:** Stable request/response examples for package 5, readable briefs and job status with result references.

**Boundary:** Package 4 owns queue behavior; package 3 owns shared persistence support. Both agree atomic claim, expiry, failure and reuse rules before building.

**Acceptance:** Queue admission is bounded; matching authorized requests may reuse a run; unrelated identities cannot read it. A completed job can correctly expose a pending approval. Unknown provider cost remains unknown. Two audience views use the same checked facts. No arbitrary SQL or unvalidated fact enters through an endpoint.

## Package 5 — User experience

**Input:** Approved response examples, status meanings, evidence and action consequences.

**Build:** Brief-first navigation, truthful comparisons, evidence drill-down, clear limitations, contextual questions, approval preview, audit and trust views. Handle loading, empty, partial, failed, expired and rejected states. Support keyboard focus, Escape, reduced motion and responsive reading.

**Output:** Screens connected to package 4; user decisions remain requests for backend validation.

**Acceptance:** Current and baseline readings share an honest scale and explicit units. Rate charts do not mix numerator shares with rates. Switching tenant/date cancels or ignores stale responses. The user can understand exactly what a proposed action will do. Copy/export preserves context and evidence. No simulated action is described as a real vendor communication.

## Package 6 — Quality and observability

**Input:** Shared acceptance cases, component outputs, run events and usage metadata.

**Build:** Golden, corrupted-data, access, prompt-abuse, approval, duplicate-effect, recovery and accessibility checks. Trace authorization, agents, tools, verification and approval outcomes without secrets or raw personal data. Measure fresh work, cached reads and provider-assisted work separately.

**Output:** A release checklist with actual observations, scope and pass/fail results; a measured latency/usage report.

**Acceptance:** G1, G2 and G3 pass; no cross-tenant leaks, unauthorized effects or invented displayed numbers. Provider timeout/malformed output uses a visible fallback. Parallel spans have correct parents and meaningful durations. No measured value is claimed before its check runs.

## Dependency order

| Stage | Main work | Who can work together | Gate before continuing |
|---|---|---|---|
| 0 — Agree interfaces | Required records, metric meanings, statuses and examples | Integration owner with all producers/consumers | Everyone accepts one contract set |
| 1 — Foundations | Ingestion, access/control interfaces, workflow shell and typed UI/API examples | Packages 1, 2, 3, 4 and 5 within agreed scope; 6 prepares tests | Each component returns a valid example or explicit error |
| 2 — First full brief | Real metrics → bounded investigation → verified report → screen | 1 + 2 + 4 + 5; 6 checks the path | Primary golden brief with evidence and no effect |
| 3 — Controlled action | Preview → human decision → fresh recheck → simulated effect → audit | 2 + 3 + 4 + 5; 6 checks misuse/repeats | One authorized effect, rejection/expiry safe |
| 4 — Resilience and finish | Degraded data, optional provider, bounded caching/queueing, restart and performance | All owners against their checks | Whole journey passes using the actual supplied data |

Use agreed fake examples only to unblock interface work. A component is not accepted until its real path is checked. The optional provider must not be necessary for the basic demonstration.

## Handoff from every person

Provide these six items:
1. What responsibility is complete, and which cases are still incomplete.
2. Input and output examples, including one failure case.
3. Contract version and dependencies on other owners.
4. Actual checks run and observed outcomes.
5. Known limitations, resource limits and recovery behavior.
6. Exact integration order and any shared-interface change that needs coordination.

Do not mark the whole product complete because one component passes. The integration owner accepts the end-to-end outcome.

## Smaller teams

- Four people: combine access/actions with API/reporting; combine quality with integration; retain dedicated data and workflow/UI ownership as capacity allows.
- Three people: data/metrics; workflow/access/API with integration; UI/quality. Each shared interface still has a named producer and consumer.
- Two people: backend journey and frontend journey; assign one as integration owner and divide independent test review explicitly.

Adjust staffing, not the security or evidence boundaries.
