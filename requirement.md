# Mobility Decision Copilot — requirements and acceptance

Version 1.1 · 2026-09-05 · Selected under D-052

Companion: [selected architecture](architecture.md). This is a build specification, not a claim of implementation completion or measured performance. The organizer's statement, explicit user decisions and approved data/metric contracts take precedence over examples in imported guides.

## 1. Purpose and outcomes

Turn the supplied mobility records into prioritized exceptions, contextual evidence, bounded recommendations and leadership-ready reports. Demonstrate sense → reason → act through a complete, observable workflow.

| ID | Outcome | Acceptance evidence |
|---|---|---|
| BO-01 | Reduce investigation effort | A manager can move from a prioritized issue to its contributors and evidence through a clear, short flow. |
| BO-02 | Contextualize facts | Each surfaced issue has an eligible historical, configured-target or authorized peer comparison, or states why the reference is unavailable. |
| BO-03 | Act proactively | A scheduled/configured historical run can detect and investigate a material issue without a user query. |
| BO-04 | Support a controlled decision | Verified findings lead to a policy-checked proposal, authorized approval, revalidation, mock effect and audit. |
| BO-05 | Support leadership | The same verified evidence produces a concise forwardable report with decisions, caveats and methodology. |
| BO-06 | Control latency and AI use | Deterministic calculations, bounded work, reusable evidence, provider limits and observable failures keep the workload inspectable. |

Primary persona: Transport Manager. The same evidence serves the Transport and Facilities Head through a leadership view. A separate Team/Line Manager experience is optional. The organizer requires at least one persona, not three complete applications.

## 2. Initial scope and conditional additions

| Initial delivery | Conditional or later |
|---|---|
| Official CSV ingestion, profile, capabilities and reconciliation | General historical ETL platform and arbitrary import formats |
| Required M01–M18 governed contracts, delivered in golden-path order | New metrics or changed formulas only through a versioned decision |
| Proactive issue detection and selective investigation | Optional clustering and richer incident follow-up |
| Four agent roles, eighteen main nodes, seven analytical workers | New agents only for a demonstrated independent responsibility |
| Exception workspace, evidence, approval, audit and leadership output | Replay controls, GPS map only with new supported location data |
| Contextual questions using the same governed tools | Generic unrestricted chatbot |
| Server-side optional Sarvam with deterministic fallback | Mandatory provider dependency or additional model infrastructure |
| Local runnable application; durable control-state option | Production identity federation, real vendor integrations, distributed streaming |
| Dataset quality counts and metric-specific caveats | Full persistent row-level quality ledger and analytical publication/rollback |
| Versioned metric registry and structured evidence | Document RAG only after corpus and retrieval gates pass |

No external vendor or employee communication is required. Initial actions are mock actions. Production-grade authentication is outside the organizer's expectation; server-side tenant authorization and governed action controls remain required.

## 3. Official dataset contract

The [dataset profile and metric contracts](docs/dataset-profile-and-capability-matrix.md) are authoritative. The [standalone team data rules](docs/team-handbook/data-and-metric-rules.md) provide the corresponding handout.

| Domain | Official files | Data rows in the approved profile |
|---|---|---:|
| Trips | Three monthly files | 615,546 |
| Employee legs | One file | 1,637,906 |
| Feedback | One file | 512,873 |
| Alerts | One file | 51,699 |
| Billing | One file | 620,942 |
| Total | Seven CSV files, May–July 2026 | 3,438,966 |

These are reference observations to reconcile on import, not hard-coded runtime counters. Preserve original bytes under the configured official-data location. Synthetic fixtures remain separately labeled and never define business targets or unsupported real-data capabilities.

Required invariants:
- Canonical trip identity is the typed pair (business_unit, trip_id), never trip_id alone.
- Normalize documented thousands separators and validate identifiers; do not repair arbitrary malformed strings by keeping only their digits.
- Apply source-specific date and numeric parsing, monthly schema reconciliation and the approved wall-clock interpretation of source epochs. Keep UTC application audit timestamps distinct.
- Preserve data versions, checksums, parser/configuration versions and quality reasons.
- Distinguish invalid fields, rejected rows, unmatched child rows, duplicates and per-metric exclusions.
- Report both child-row match coverage and parent-trip participation with named numerators/denominators.
- Treat placeholder riders, billing adjustments, rating zeros, invalid severity and extreme delays according to the approved contracts.
- Use office × shift × direction as a route proxy. The files contain no GPS coordinates, route IDs, driver IDs, budget values or free-text feedback.

## 4. Governed business rules

This table maps requirements to the accepted contracts; it does not create another metric catalog. Exact eligibility, calculations, dimensions and exclusions remain in M01–M18 v1.1 and D-039.

| ID | Binding rule |
|---|---|
| BR-01 | Every request, join, evidence object, cache, action and audit is tenant-scoped. Cross-tenant comparisons require an explicitly authorized role and scope. |
| BR-02 | M01 is the trip-level delayed-trip rate using delay_minutes > 0. Its configured rate target is separate from that row-level definition. |
| BR-03 | M04/M05 measure employee-leg pickup/drop punctuality within ten minutes using the corresponding epochs and approved boarded-leg eligibility. Five-minute exploratory OTA is not a replacement. |
| BR-04 | M08 caps occupancy at capacity while retaining an explicit over-capacity quality flag and the raw source value. |
| BR-05 | M06 includes all valid non-placeholder employee legs after approved deduplication; do not exclude no-shows by requiring arrival or boarding. |
| BR-06 | M09 aggregates retained billing lines per composite trip, applies exclusions and reports the median eligible trip cost. M10 follows its billed-distance rule and per-tenant capability gate. |
| BR-07 | M11 is driver-only low-rating rate; M12 excludes non-positive ratings. Report the correct participation/rating coverage and low-coverage caveats. |
| BR-08 | M13/M14 exclude the sign-off event regime change; M15 is eligible Sev-1/2 acknowledgement P90. Preserve unknown-severity quality reasons. |
| BR-09 | Historical comparison uses the approved prior complete weeks; peer ranking preserves cohort and minimum-volume rules. Configuration changes are versioned. |
| BR-10 | M02 uses the approved cap/quarantine rules while preserving late flags. Do not change evidence through chart-only clipping or unapproved cleaning. |
| BR-11 | M16 is a tracking-alert proxy, M17 is EV trip share, and M18 is descriptive escort presence. No GPS, emissions or compliance guarantee follows from them. |
| BR-12 | Missing or unsupported metrics are explicit unavailable/partial results, never zero. Configured targets are labeled as configured, never organizer-supplied SLA values. |

## 5. Functional requirements

Each requirement needs an owner, a test or inspection criterion, and completion evidence in the team delivery plan.

### Data and quality

| ID | Requirement and acceptance |
|---|---|
| FR-DATA-01 | An authorized import/profile operation reads configured immutable sources and records file identity, versions, counts, duration and result. |
| FR-DATA-02 | Parsing handles known format drift without overwriting source bytes or silently coercing malformed identifiers. |
| FR-DATA-03 | All child joins use the composite trip key; cross-tenant collisions cannot attach records to another tenant. |
| FR-DATA-04 | Invalid fields and rows carry explicit reasons. Metric eligibility is evaluated separately so an invalid distance does not remove an otherwise valid observation from unrelated metrics. |
| FR-DATA-05 | Reconciliation names physical/parsed/accepted/rejected/duplicate/unmatched populations and avoids double-counting overlapping quality categories. |
| FR-DATA-06 | Repeating an import with the same content and configuration cannot duplicate accepted records. Document supported restart behavior rather than assuming arbitrary-step recovery. |

### Analytics and experience

| ID | Requirement and acceptance |
|---|---|
| FR-AN-01 | The workspace shows authorized scope, reporting period/as-of date, source freshness, governed readings and valid comparison context. |
| FR-AN-02 | Drill-down uses supported vendor, site, shift, direction, mode and date dimensions, with population, exclusions and capability reasons. |
| FR-AN-03 | Numeric answers come from governed parameterized tools. Neither the model nor browser reimplements metric arithmetic. |
| FR-AN-04 | Each displayed fact resolves to its metric/query definition, filters, data/metric version and supporting evidence. |
| FR-AN-05 | Charts use backend values with explicit units, denominators and warnings; partial or unavailable data is visible. |
| FR-AN-06 | Sustainability initially reports supported fuel/EV share. Do not infer emissions savings without a separately approved model and inputs. |

### Four-agent workflow

| ID | Requirement and acceptance |
|---|---|
| FR-AG-01 | The Supervisor proposes an allowed bounded investigation plan for a query or proactive issue; deterministic validation preserves mandatory comparisons. |
| FR-AG-02 | The Investigator selects governed analytical workers, validates their structured results and stops within shared tool/depth/time limits. |
| FR-AG-03 | The Evidence Critic challenges unsupported interpretation and vendor blame; deterministic verification checks claims against the evidence actually cited. |
| FR-AG-04 | The Briefing/Action role selects and explains verified findings and drafts a bounded proposal. Deterministic services own policy and action state. |
| FR-AG-05 | Reporting produces operational and leadership output from the same verified facts, with required methodology and caveats. It is not an extra permanent agent. |
| FR-AG-06 | Insufficient evidence produces a limitation or bounded clarification; unsupported numbers, sources and causal claims are not invented. |
| FR-AG-07 | Safe retries and one correction cycle remain bounded. Failure, cancellation, partial evidence and fallback are visible; abandoned work is not blindly replayed. |
| FR-AG-08 | Runs retain identity/scope, versions, plan, safe tool records, evidence, usage, timings and approval outcome, with redaction. |

### Questions, proactive work, actions and reports

| ID | Requirement and acceptance |
|---|---|
| FR-UX-01 | Contextual questions use the same authorization, metrics and evidence path as the proactive brief. |
| FR-UX-02 | Ask a focused clarification when ambiguity materially changes the requested result; otherwise use visible context. |
| FR-PR-01 | A configured historical schedule creates a scoped, deduplicated brief job and can complete without a manual query. |
| FR-PR-02 | Reuse is identity/version-aware. An optional incident persistence feature must distinguish issue identity, recurring detection and actual resolution. |
| FR-ACT-01 | Approval binds an authorized actor to an exact proposal and evidence version; fresh revalidation precedes an idempotent mock effect and receipt. |
| FR-ACT-02 | Rejected, expired, unauthorized or stale proposals cannot execute. Edits undergo policy and approval validation; every outcome is audited. |
| FR-REP-01 | Leadership output is forwardable printable HTML or an equivalent supported export with period, scope, findings, decisions, caveats and methodology. |

### Conditional knowledge requirements

FR-RAG-01 through FR-RAG-04 are reserved for a later document-dependent extension: authorized ingestion with provenance, tenant/ACL-filtered retrieval, SQL authority for structured facts, and document version retirement. They are not initial release blockers.

Activation requires a decision-relevant document corpus, at least five document-dependent golden questions, enforceable source/version/access metadata, evaluated retrieval and citation quality, and a measured resource budget. Generated narratives never override their original evidence.

## 6. Non-functional and AI controls

| ID | Requirement |
|---|---|
| NFR-PERF-01 | Measure dashboard/API latency under a documented machine, data and concurrency workload, separating cold/warm and cache-hit behavior. |
| NFR-PERF-02 | Measure queue admission, queue wait and fresh investigation completion separately. A progress stream is not proof of faster analysis. |
| NFR-PERF-03 | Record import time, peak memory and reconciliation results on official data; test restart/idempotency for explicitly supported failure paths. |
| NFR-SCALE-01 | Bound queues, worker pools, caches and provider concurrency; expose saturation and errors. Larger datasets and multiple replicas require separate workload evidence. |
| NFR-REL-01 | Durable mode preserves job/approval/action state and prevents duplicate claims/effects. Document recovery limits and fail visibly on unsafe replay. |
| NFR-REL-02 | Provider timeouts, bounded safe retries/cooldown, schema validation and deterministic fallback preserve usable outputs without external connectivity. |
| NFR-COST-01 | Send compact governed evidence, not raw fact tables; record provider attempts, real usage, configured cost estimates and fallback separately. |
| NFR-SEC-01 | Enforce tenant/resource authorization at API, tool, evidence, cache/reuse, action and audit boundaries. |
| NFR-SEC-02 | Keep provider keys server-side and outside committed files, browser payloads and logs. |
| NFR-SEC-03 | Deterministic role/policy checks govern data access, export, configuration and approval. |
| NFR-PRIV-01 | Minimize rider-level information; redact unnecessary identities from prompts, logs and reports. |
| NFR-OBS-01 | Correlate request, job, run, tool, data version and audit references. Keep business audit separate from optional diagnostic trace export. |
| NFR-A11Y-01 | Primary flows support keyboard operation, visible focus, sufficient contrast, text alternatives and reduced motion, with accessibility validation. |
| NFR-MAINT-01 | Components and four agent roles communicate through versioned typed boundaries; ownership is explicit. |
| NFR-PORT-01 | The provider and persistence adapters can change without changing metric authority, workflow policy or public facts. |

The incoming two-second/twelve-second p95 figures and ten-million-row goal are proposed benchmark experiments, not guaranteed release claims. Adopt numeric operational SLOs only with a named workload, owner and measurement; do not infer performance from an architecture diagram.

AI controls AI-01–AI-09 remain mandatory: treat retrieved/data content as untrusted; allowlist and validate tools; prohibit arbitrary generated SQL; require approval for external effects; distinguish hypotheses from facts; restrict sensitive-segment use; test injection and tenant/approval bypass; version prompts/models/evidence; and retain non-LLM operation.

## 7. Release acceptance

Use the existing 30–50-case evaluation range with all risk categories represented. Deterministic tests decide numerical correctness, authorization, evidence validity and action behavior. Reviewed semantic rubrics may supplement them; a model judge cannot pass a release alone.

| ID | Gate |
|---|---|
| AC-01 | Official-data metric fixtures reproduce M01–M18 semantics and approved rounding/eligibility, rather than only synthetic examples. |
| AC-02 | Every displayed factual claim is supported by its cited evidence, scope, units, dates and version. Unsupported claims are suppressed or explicitly bounded hypotheses. |
| AC-03 | Cross-tenant joins, resource lookups, cache reuse and unauthorized cross-tenant questions do not leak data. |
| AC-04 | No action bypasses policy, valid approval, fresh revalidation, idempotency and audit; rejection/expiry/edit/duplicate attempts are covered. |
| AC-05 | G1 reaches a site/shift explanation, rejects unsupported single-vendor blame and completes a watchlist/investigation mock-action flow. |
| AC-06 | G2 carries its low-coverage and unsupported-cost-per-km caveats; G3 produces a data-quality note and does not escalate. |
| AC-07 | One official-data proactive flow, contextual question, approval/receipt flow and leadership report work end to end. |
| AC-08 | Relevant data, workflow, security, provider-failure, recovery and UI tests pass, with performance results labeled by workload and mode. |

Retain the approved corrupted-data cases: missing legs, missing bills, shuffled feedback keys, duplicate trips and unavailable alert severity. Generate them as copies; do not mutate the official source. Add saturation, cancellation, stale-evidence and provider-fallback tests where those behaviors are implemented.

## 8. Delivery and traceability

Freeze shared records and their producing/consuming owners before parallel work. Use the [six team packages](docs/team-handbook/team-work-packages.md) plus a designated integration owner; combine packages for a smaller team.

For each task record requirement ID → component owner → input/output contract → dependency → acceptance evidence. Build the complete G1 path before replay or retrieval. Keep human product/release review concise and concrete; imported process documents do not introduce new blanket permission gates.

## 9. Decisions settled for this build

| Former open question | Selected decision |
|---|---|
| Five-minute OTA | Retain separate M01 and M04/M05 contracts; exploratory OTA is not the headline. |
| Rating-zero semantics | Use the approved rating contracts and show the correct coverage. |
| Number of agents | Four roles with an explicit critic and deterministic verification. |
| Browser/backend runtime | React/TypeScript and Java/Spring Boot; no Streamlit/Python or Angular migration by default. |
| Analytical/control storage | DuckDB analytics; PostgreSQL durable control with explicit local fallback. |
| Sarvam | Optional server-side adapter, contract validation, bounded use and deterministic fallback. |
| RAG and replay | Conditional additions after the golden path, not initial dependencies. |
| External communications | Mock actions only for the prototype. |

D-052 records the selection. Use these requirements, the selected architecture and the standalone team handbook as the aligned build instructions.
