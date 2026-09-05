# Mobility Decision Copilot: Detailed Solution Architecture

Date: 2026-09-04  
Status: Frozen; bound to the official dataset on 2026-09-04 (see `dataset-profile-and-capability-matrix.md` and D-029 through D-033)

## 1. Architecture decision

Build a **controlled multi-agent analytical workflow**, not an autonomous swarm and not a document chatbot:

- Four LLM specialists with separate prompts, inputs, outputs, and permissions.
- Two components make dynamic agentic decisions: Supervisor and Investigator. Only the Investigator runs a bounded tool-selection loop.
- Eighteen top-level workflow nodes plus one reusable four-node investigation subgraph, executed through a LangGraph4j adapter or the deterministic Java state-machine fallback.
- DuckDB and governed metric contracts calculate operational facts.
- Langfuse observes and evaluates the workflow; it is not the audit ledger.
- No document RAG or neural reranker in the mandatory path while the only resource is structured trip logs.
- A conditional fifth Knowledge Agent and seven-node retrieval subgraph only if policies, SLAs, contracts, SOPs, or historical reports arrive.
- Dataset bindings: tenant is `business_unit`, the trip key is `(business_unit, trip_id)`, and the supported analyses per tenant come from the capability matrix in `dataset-profile-and-capability-matrix.md`. There are no GPS, driver, route, SLA or budget fields; see Section 4a.

The design distinguishes predetermined workflows from agents that dynamically choose tools. This system deliberately combines them instead of making every step agentic. The orchestration pattern is framework-neutral; the live implementation uses Java interfaces with either the validated LangGraph4j adapter or a deterministic state-machine fallback. See the conceptual [LangGraph workflows and agents](https://docs.langchain.com/oss/python/langgraph/workflows-agents) reference.

## 2. Vocabulary

| Concept | Meaning here | Example |
|---|---|---|
| Agent | LLM component making a bounded semantic decision or tool selection | Investigator chooses the next approved analysis |
| Node | One graph state transition with one responsibility | `verify_evidence` |
| Tool | Typed deterministic function exposed to an agent | `rank_contributors(...)` |
| Service | Non-LLM application component | DuckDB metric engine or audit repository |
| Worker task | Isolated invocation of a reusable subgraph | Vendor-contribution investigation |

Multi-agent quality comes from meaningful context, tool, and policy boundaries—not agent count.

## 3. Four LLM specialists

### A1. Supervisor and Planning Agent

- **Purpose:** Turn the selected issue or question into a small investigation plan.
- **Input:** Persona, request mode, capability matrix, anomaly/question, available tools, and remaining budget.
- **Output:** Typed `InvestigationPlan` containing required metrics, comparisons, tasks, dependencies, allowed dimensions, and stop conditions.
- **Permissions:** No raw SQL, retrieval, calculation, or action execution. It selects only registered tasks.
- **Boundary:** Broad planning context, zero authority to create facts.

### A2. Investigation Agent

- **Purpose:** Resolve one bounded task with governed read-only analytical tools.
- **Input:** One task, compatible tools, existing evidence summaries, and budget.
- **Output:** Evidence IDs, measured contributors, coverage, quality warnings, direct findings, inferences, and unresolved questions.
- **Permissions:** No arbitrary SQL, writes, cross-tenant access, or unrestricted APIs.
- **Limits:** Configured tool-call, row, time, token, and cost limits.

### A3. Evidence Critic

- **Purpose:** Challenge draft findings before they reach the user.
- **Input:** Evidence package, claims, metric definitions, and claim rules.
- **Output:** Supported claims, overclaims, missing caveats, contradictions, and pass/revise/abstain.
- **Permissions:** No tools, database, retrieval, or actions.
- **Limit:** One review and at most one correction cycle.

### A4. Briefing and Action-Drafting Agent

- **Purpose:** Create the operations brief, leadership narrative, and bounded action proposal from verified evidence.
- **Input:** Verified findings, persona templates, confidence/caveats, and allowed action types.
- **Output:** Typed `DecisionBrief` and `ActionProposal`.
- **Permissions:** No database, retrieval, or execution. It cannot introduce a factual claim without an evidence ID.

### Conditional A5. Knowledge Agent

Add only if documents contain decision-relevant knowledge absent from structured fields. It returns cited `DocumentEvidence` and has no metric or action permissions.

## 4. Why data domains are workers, not agents

Vendor, site-shift-direction, delay-reason, cost and billing, feedback, tracking and safety alerts, and no-show and roster analyses use the same tenant, governed engine, policy boundary, and result schema. They are parallel worker tasks executed through the same Investigation Agent subgraph.

Separate permanent agents would add prompts, state, model calls, latency, and merging failures without creating a real boundary. The Java orchestrator dispatches isolated, typed investigation tasks when the validated plan calls for them and merges them with explicit provenance. See the conceptual [orchestrator-worker guidance](https://docs.langchain.com/oss/python/langgraph/workflows-agents#orchestrator-worker).

### 4a. Worker tasks bound to the official dataset

| Worker task | Source tables | Governed metrics | Availability |
|---|---|---|---|
| `vendor` | rides, bills, feedback, alerts | M01-M03, M09-M14 by `vendor_id` | all tenants; peer ranking needs 500 trips |
| `site_shift_direction` | rides, legs | M01, M04, M05 by `office × shift_type × trip_direction` | all tenants; single-office tenants get a caveat |
| `delay_reason` | rides | M03 | all tenants |
| `cost_billing` | bills | M09, M10 | M10 unsupported for `vanta-Aus` and `vanta-Sea` |
| `feedback` | feedback | M11, M12 | low coverage for `vanta-Aus`, `vanta-Sea`, `catalyst-Sac` |
| `tracking_safety_alerts` | alerts | M13-M16, M18 | event types vary by tenant; sign-off violations excluded |
| `noshow_roster` | legs | M06, M07 | all tenants |

There is no GPS worker. Location, speed and geofence analysis beyond the alert counts is marked unsupported in the capability matrix.

## 5. Main graph: 18 nodes

`START` and `END` are not counted.

| # | Node | Type | Responsibility | Failure/degraded route |
|---:|---|---|---|---|
| 1 | `initialize_run` | Deterministic | Create run, trace, data version, request mode, budget, and tenant-safe IDs | Reject malformed request |
| 2 | `authorize_scope` | Deterministic | Enforce tenant, persona, metrics, dimensions, and tools | Fail closed |
| 3 | `profile_dataset` | Deterministic | Load the versioned schema/data-quality profile; calculate it only when the data version changes | Continue with supported capabilities |
| 4 | `build_capability_matrix` | Deterministic | Load/build the per-data-version matrix of supported, derivable, and unavailable analyses | Disable unsupported branches |
| 5 | `compute_metric_snapshot` | Deterministic | Load/compute cached versioned metrics, baselines, population, and freshness for the requested window | Quality-qualified snapshot |
| 6 | `detect_anomalies` | Deterministic | Compare against available SLA, history, or peers | Healthy-brief route |
| 7 | `prioritize_issue` | Deterministic | Select the highest-value issue with configurable materiality/confidence rules | Low-confidence investigation only |
| 8 | `supervisor_plan` | LLM A1 | Create bounded typed plan | Parse retry once, then fallback |
| 9 | `validate_plan` | Deterministic | Check tools, dimensions, capabilities, budget, and dependencies | Remove invalid tasks or stop |
| 10 | `run_investigations` | Subgraph A2 | Dispatch isolated tasks through the reusable investigator | Preserve successful branches |
| 11 | `merge_evidence` | Deterministic | Deduplicate and link evidence to data/metric versions and claims | Flag contradictions/gaps |
| 12 | `evidence_critic` | LLM A3 | Detect unsupported language and missing caveats | Revise once or abstain |
| 13 | `verify_evidence` | Deterministic | Enforce arithmetic, provenance, freshness, policy, and confidence | Safe partial answer |
| 14 | `compose_decision_brief` | LLM A4 | Produce dual brief and action draft | Deterministic template fallback |
| 15 | `action_policy_gate` | Deterministic | Validate action, parameters, evidence, expiry, and authorization | Report-only/reject route |
| 16 | `approval_interrupt` | Human | Pause for approve, reject, or edit with serializable payload | Rejection/expiry to audit |
| 17 | `revalidate_and_execute_mock_action` | Deterministic | After approval, recheck authorization, evidence version, expiry, parameters, and preconditions; then idempotently create the mock escalation/ticket/watchlist/draft | Bounded retry, never duplicate |
| 18 | `append_audit_event` | Deterministic | Persist run, evidence, decision, approval, and receipt refs | Surface ledger failure |

```mermaid
flowchart TD
    S([START / schedule / load / question]) --> N1[1 initialize_run]
    N1 --> N2[2 authorize_scope]
    N2 --> N3[3 profile_dataset]
    N3 --> N4[4 build_capability_matrix]
    N4 --> N5[5 compute_metric_snapshot]
    N5 --> N6[6 detect_anomalies]
    N6 -->|healthy schedule| H[healthy brief]
    N6 -->|issue or question| N7[7 prioritize_issue]
    N7 --> N8[8 supervisor_plan - A1]
    N8 --> N9[9 validate_plan]
    N9 --> N10[[10 investigation subgraph - A2]]
    N10 --> N11[11 merge_evidence]
    N11 --> N12[12 evidence_critic - A3]
    N12 --> N13[13 verify_evidence]
    N13 -->|one revision allowed| N8
    N13 -->|pass or qualified| N14[14 compose_decision_brief - A4]
    N14 --> N15[15 action_policy_gate]
    N15 -->|report only / reject| N18[18 append_audit_event]
    N15 -->|eligible| N16{{16 approval_interrupt}}
    N16 -->|reject / expire| N18
    N16 -->|approve| N17[17 revalidate and execute mock action]
    N17 --> N18
    H --> N18
    N18 --> E([END])
```

The revision edge requires `revision_count <= 1`. Every loop has hard tool-call, time, token, and cost limits.

## 6. Investigation subgraph: four nodes

`run_investigations` invokes this subgraph per validated task. Independent calls may run in parallel.

| Node | Type | Responsibility |
|---|---|---|
| `choose_analysis` | LLM A2 | Select one next governed tool or finish |
| `execute_analysis` | Deterministic | Run a registered parameterized query/computation |
| `validate_tool_result` | Deterministic | Check schema, provenance, coverage, row limits, and errors |
| `progress_gate` | Deterministic | Continue, stop, qualify, or return partial evidence |

```mermaid
flowchart LR
    I([task]) --> C[choose_analysis - A2]
    C --> X[execute_analysis]
    X --> V[validate_tool_result]
    V --> G{progress_gate}
    G -->|more evidence and budget| C
    G -->|complete / partial / exhausted| O([InvestigationResult])
```

Give every parallel investigation task an isolated run-scoped state object and merge only typed evidence envelopes into the parent state. The checkpoint repository persists the parent workflow and completed task results by run ID; this contract must behave identically under LangGraph4j and the fallback state machine.

## 7. Governed analytical tools

The LLM selects registered metric IDs, dimensions, filters, and comparison modes; it never writes arbitrary SQL.

```text
get_metric(metric_id, window, filters, grain)
compare_metric(metric_id, current_window, reference_mode, filters)
rank_contributors(metric_id, dimension, window, filters, min_volume)
get_distribution(metric_id, dimension, window, filters)
estimate_impact(issue_id, impact_model_id)
get_quality_report(fields_or_metric_ids)
get_bounded_examples(evidence_id, limit, redaction_policy)
simulate_action(action_type, target_ids, parameters)
```

Every tool result includes evidence/query ID, metric contract/version, data version, window, filters, grain, numerator, denominator, value/unit, comparison, population, coverage, warnings, and tenant-safe provenance.

The metric registry owns definition, formula implementation, owner, allowed dimensions, exclusions, unit, grain, freshness, authorization, and edge-case behavior. Incompatible groupings are rejected, not approximated. The v1 registry is M01-M18 in `dataset-profile-and-capability-matrix.md` Section 8, with allowed dimensions `vendor_id`, `site_id`, `shift_id`, `direction`, `mode`, `fuel_type`, `vehicle_id` and comparison modes historical (prior four complete weeks), in-tenant peer, cross-tenant peer (facilities-head persona only) and configured target.

## 8. RAG decision

### Mandatory path: no document RAG

The live statement names structured trip logs, not a document corpus. Therefore:

- SQL retrieves operational facts.
- The metric catalog retrieves approved definitions by ID/alias.
- Evidence IDs retrieve computed results for synthesis.
- The LLM generates only from verified evidence objects.

This is retrieval-grounded generation over structured evidence, but it should not be advertised as vector RAG. Embeddings and cross-encoders do not calculate on-time arrival, cost, occupancy, or affected employees.

### Conditional activation gate

Activate document RAG only when all are true:

1. Documents contain decision-relevant knowledge absent from structured fields.
2. At least five golden questions require policy/SLA/contract/SOP evidence.
3. Tenant, role, version, effective date, page/section, and citations are preservable.
4. Retrieval evaluation beats a lexical-only baseline inside the latency budget.

If any gate fails, RAG stays disabled.

## 9. Conditional hybrid RAG and reranking: seven nodes

If the gate passes, add A5 and a subgraph parallel to analytical investigation:

| Node | Responsibility |
|---|---|
| `classify_knowledge_need` | Form the policy query and required authority/effective date |
| `authorize_and_filter` | Apply tenant, role, document type, entity, version, and effective-date filters before search |
| `parallel_retrieve` | Run BM25/lexical and embedding retrieval for recall |
| `fuse_candidates` | Reciprocal-rank fusion while retaining component ranks/provenance |
| `rerank_candidates` | Second-stage cross-encoder or evaluated constrained-model ordering against the original question |
| `dedupe_and_pack` | Remove overlap and preserve authority/diversity within context budget |
| `verify_citations` | Validate passage, version, authorization, and claim support |

Evaluation starting point, not a fixed configuration:

- Retrieve about 15-25 candidates from each first-stage retriever.
- Fuse and rerank roughly 15-25 unique candidates.
- Pass about 4-6 authoritative, non-duplicative passages.
- Treat scores as ranking diagnostics, not confidence probabilities.

Tune with evidence recall, MRR/nDCG, citation precision, duplicate rate, latency, cost, and ACL violations. Reranking is a second-stage ordering mechanism over a high-recall candidate set; see [Cohere Rerank](https://docs.cohere.com/v2/docs/rerank).

Do not initially add HyDE, multi-query expansion, knowledge graphs, OpenKB/PageIndex, LLM-only reranking, whole-document prompting, or post-generation access filtering. Add a strategy only to fix a measured failure.

## 10. Two ranking problems

### Operational prioritization

Rank anomalies using deterministic configurable features: safety override, configured-target/trend gap, affected trips/rider legs, cost impact, persistence, coverage/confidence, and minimum volume. Initial thresholds from profiling: a material issue needs at least 300 trips in the window, an absolute gap of 3 percentage points and a relative rise of 25% against the prior four complete weeks for rate metrics, or any Sev-1/2 alert-rate doubling. A step change confined to one alert type that falls to near zero (the sign-off-violation case, G3) is classified as a data-regime change and routed to a data-quality note, not an issue. Expose score components in the UI.

### Document reranking

Order policy/document passages after ACL-filtered hybrid retrieval. It is conditional and never alters an operational metric.

## 11. Shared typed state

```python
class MobilityState(TypedDict):
    run: RunContext
    request: RequestContext
    budget: ExecutionBudget
    data_profile: DataProfile
    capabilities: CapabilityMatrix
    metric_snapshot: list[MetricEvidence]
    anomaly_candidates: list[Anomaly]
    selected_issue: Anomaly | None
    investigation_plan: InvestigationPlan | None
    investigation_results: Annotated[list[InvestigationResult], operator.add]
    document_evidence: Annotated[list[DocumentEvidence], operator.add]
    evidence_package: EvidencePackage | None
    critique: Critique | None
    verification: VerificationResult | None
    decision_brief: DecisionBrief | None
    action: ActionProposal | None
    approval: ApprovalDecision | None
    execution_receipt: ExecutionReceipt | None
    errors: Annotated[list[WorkflowError], operator.add]
    audit_event_ids: Annotated[list[str], operator.add]
```

All models are JSON-serializable and versioned. Use explicit reducers for parallel lists; keep large data outside graph state behind evidence IDs.

## 12. Approval, recovery, and audit

Run the workflow with a checkpoint repository and tenant-safe run/conversation ID. A local relational adapter is acceptable for the demo; PostgreSQL is the production story. Persist workflow checkpoints separately from cross-run memory and business audit events.

Approval payload:

```json
{
  "action_id": "...",
  "action_type": "create_vendor_investigation",
  "targets": ["vendor-token"],
  "reason": "...",
  "evidence_ids": ["..."],
  "expected_effect": "...",
  "risk": "low",
  "expires_at": "...",
  "idempotency_key": "..."
}
```

Rules:

- Put `interrupt()` in a dedicated approval node.
- Never perform a non-idempotent write before it; either orchestration adapter may re-enter the approval node during resume or retry.
- Recheck authorization, parameters, evidence version, expiry, and preconditions after approval.
- Enforce idempotency at the action repository boundary.
- Append an immutable audit event at proposal, approval/rejection/edit, execution attempt, receipt, and final status; node 18 also writes the terminal run summary.

The original [LangGraph interrupt rules](https://docs.langchain.com/oss/python/langgraph/interrupts#rules-of-interrupts) remain useful design guidance, but the Java spike must prove the actual LangGraph4j resume semantics before relying on them.

## 13. Verification and confidence

The deterministic verifier ensures:

- Every displayed number exists in evidence.
- Units, filters, populations, and periods are compatible.
- Current/reference windows are valid.
- Claim evidence is present and authorized.
- Missing feedback coverage, unsupported cost-per-km, single-office tenants, capped delays and excluded billing adjustments appear in caveats.
- “Caused” is rejected without causal evidence; use “contributed,” “associated,” or “coincided.”
- Sparse groups are suppressed or qualified.
- Action type and parameters are allowlisted.

Confidence is computed from data coverage/quality, statistical/material support, agreement across signals, freshness, and investigation completeness—not the LLM's self-rating.

## 14. Langfuse trace

One graph run is one root trace:

```text
mobility_run
  authorize_scope
  profile_dataset
  compute_metric_snapshot
  detect_anomalies
  supervisor_plan
  investigation.vendor
    choose_analysis
    tool.rank_contributors
  investigation.tracking_safety_alerts
    choose_analysis
    tool.get_quality_report
  merge_evidence
  evidence_critic
  verify_evidence
  compose_decision_brief
  approval_interrupt
  revalidate_and_execute_mock_action
  append_audit_event
```

Record safe tenant, run/thread ID, workflow/prompt/model/metric/data versions, task/tool, evidence count, latency, usage/cost, retries, outcome, approval, and audit ID. Redact PII and secrets.

Langfuse accepts language-neutral OpenTelemetry spans, so the Java runtime can instrument Spring AI, graph nodes, tools, approval transitions and DuckDB queries without allowing observability to dictate orchestration. See [Langfuse tracing](https://langfuse.com/docs/observability/get-started).

## 15. Evaluation

### Deterministic tests

- Hand-calculated metric fixtures for nulls, duplicates, cancellations, zero denominators, capped delays, negative bills and cross-tenant `trip_id` collisions (the ten fixtures in `dataset-profile-and-capability-matrix.md` Section 11).
- Capability behavior without legs, bills, severity, feedback coverage or billed km (variants V1-V5).
- Plan validator blocks arbitrary SQL, unsupported dimensions, excess tasks, and cross-tenant scope.
- Verifier catches changed numbers, missing evidence, incompatible periods, and unsupported causal claims.
- Approval reject/edit/expiry, crash/resume, and duplicate execution.

### Trajectory tests

- Correct tool category and maximum calls.
- Stops when evidence is sufficient.
- Returns partial evidence when one branch fails.
- Abstains when evidence is inadequate.
- Uses at most one correction cycle.

### Narrative tests

- Operations and leadership outputs contain identical facts.
- Every factual claim resolves to evidence.
- Missing-data caveats are visible.
- LLM judges assess only clarity, relevance, and explanation groundedness—not arithmetic, security, or authorization.

Golden cases G1, G2 and G3, the ten fixtures and the five corrupted variants are defined in `dataset-profile-and-capability-matrix.md` Section 11; extend to 20-30 cases during the build. No cross-tenant leak, wrong governed metric, unsupported citation, or unauthorized action may pass, and G3 must never escalate.

## 16. Security boundaries

- Authorize before retrieving metrics or documents.
- Use read-only, template-based analytical queries.
- Tokenize/redact employee and driver IDs in prompts, traces, and UI.
- Treat user text, dataset text, documents, peer-agent output, and tool output as untrusted.
- Validate structured output before state updates.
- Expose no shell, generic SQL/HTTP/URL/filesystem, or real vendor-system tool.
- Propose, validate, approve, revalidate, idempotently execute, and audit every write.
- Fail closed for access/actions with a useful non-sensitive explanation.

## 17. Judge-proof UI

One screen should expose:

1. Proactive morning brief and top issue.
2. Metric, SLA/history/peer comparison, population, and freshness.
3. Vendor, site-shift, delay-reason, cost, feedback and safety-alert evidence drawer, with unsupported analyses shown greyed with the reason.
4. Direct versus inferred claims, confidence components, and caveats.
5. Action, expected effect, risk, and approve/reject/edit.
6. Forwardable leadership narrative from the same evidence.
7. Trace ID, audit ID, versions, latency/cost, and evaluation status.

The dashboard is not the product; the proactive decision and controlled intervention are.

## 18. Implementation order

### Dataset intake (completed 2026-09-04)

Preserved and checksummed the seven files, profiled every table, mapped fields, built the per-tenant capability matrix, hand-reconciled M01-M18, selected G1/G2/G3 and recorded D-029 through D-033. Evidence: `dataset-profile-and-capability-matrix.md`.

### Build sequence

1. Java record/sealed-type state and output contracts with Jackson schemas.
2. DuckDB adapter: load seven CSVs, normalise keys and formats, build tenant-keyed views, reproduce the ten fixtures.
3. Metric registry with M01, M04 and M09 end to end for `pinnacle-Slc`; then the rest of M01-M18.
4. Daily snapshot cache, anomaly detection with the profiled thresholds, and G3 regime-change classification.
5. Graph skeleton with the seven workers on real tools; G1 as-of 2026-06-08 through investigation and verified dual brief.
6. Approval, revalidation, idempotency, audit and trace skeleton.
7. React/TypeScript brief, evidence drawer with capability greying, approval inbox and trust panel.
8. G2 as-of 2026-08-01 with caveats; corrupted variants V1-V5; regression gate.
9. Conversational drawer (D-027) and peer-comparison questions.
10. Optional RAG only if its gate passes.

## 19. Team split

| Owner | Responsibility | Contract |
|---|---|---|
| Data/metrics | Adapter, DuckDB, registry, anomaly/contribution tools | Typed evidence objects |
| Agent/backend | Spring Boot/Spring AI, LangGraph4j adapter or Java state machine, checkpoint/recovery | Java records, sealed types and Jackson schemas |
| Product/frontend | Brief, investigation, approval, leadership/trust views | Stable API fixtures from hour one |
| Quality/demo | Langfuse, tests, audit proof, deck, demo script | Requirement-to-proof matrix |

With three people, combine quality/demo with agent/backend. Split by engineering boundary, not one person per agent.

## 20. Cuts and final pitch

Cut in order: document RAG/A5, extra investigation dimensions, general chat, rich export, PostgreSQL/deployment polish.

Never cut: one correct governed metric, contextual benchmark, proactive trigger, evidence-backed investigation, verified dual output, approval/audit, deterministic fallback, and the composite tenant key.

Judge-facing wording:

> We use four specialized LLM roles, but only where semantic judgment helps. A typed Java orchestrator controls an 18-node workflow; a bounded investigator subgraph runs supported domain analyses in parallel. LangGraph4j is an adapter, not a business-logic dependency, and a deterministic Java state machine is the fallback. All metrics, thresholds, authorization, evidence checks, approvals, and actions remain deterministic. Because the supplied resource is structured trip data, SQL is operational truth. Hybrid RAG and reranking are activated only if decision-relevant documents arrive. Every conclusion is versioned, traceable, approval-gated, and auditable.
