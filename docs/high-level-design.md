# Mobility Decision Copilot — architecture design

This document explains responsibilities and boundaries for the team build. Start with the [plain-English handbook](team-handbook/README.md). Its companion work packages can be assigned to people without relying on reference source files.

## System purpose

Turn historical transport data into a checked operations brief and leadership summary, then support a human decision about a proposed simulated action. Every conclusion must carry its evidence and limitations.

~~~mermaid
flowchart TD
    UI["Dashboard and evidence screens"] --> API["Validated request boundary"]
    API --> AUTH["Identity and tenant access"]
    AUTH --> JOB["Bounded background request queue"]
    JOB --> FLOW["Controlled 18-step workflow"]
    AUTH -->|"Scoped question or direct request"| FLOW
    DATA["Untouched supplied data"] --> LOAD["Normalize and check data quality"]
    LOAD --> STORE[("DuckDB analytical store")]
    STORE --> METRIC["Approved metrics and bounded cache"]
    METRIC --> FLOW
    FLOW --> AGENTS["Supervisor / Investigator / Critic / Briefing"]
    AGENTS -->|"Bounded structured assistance"| MODEL["Optional Sarvam"]
    AGENTS --> EVIDENCE["Merge evidence and verify claims"]
    EVIDENCE --> REPORT["Render operations and leadership views"]
    REPORT --> POLICY["Deterministic action policy"]
    POLICY -->|"Eligible"| HUMAN["Human approve, edit or reject"]
    HUMAN --> RECHECK["Fresh evidence and proposal revalidation"]
    RECHECK --> EFFECT["One simulated effect, or non-execution reason"]
    FLOW --> CONTROL[("PostgreSQL shared control state")]
    EFFECT --> CONTROL
    REPORT --> API
    FLOW -.-> TRACE["Redacted diagnostics and acceptance evaluation"]
~~~

The workflow includes the agent and evidence steps; these are logical responsibilities, not a requirement for separate servers.

## Responsibility boundaries

| Area | Owns |
|---|---|
| Experience | Briefs, comparisons, evidence, questions, approval preview, audit and trust |
| Access/API | Identity, authorized scope, request validation, errors and response records |
| Data/analytics | Normalization, quality, capability eligibility, metric definitions and calculations |
| Workflow/agents | Bounded planning/investigation, evidence review and checked output |
| Action/control | Human decisions, revalidation, duplicate prevention, recovery and business audit |
| Quality/observability | Test evidence, node/tool timing, provider usage and safe diagnostics |

Java/Spring Boot and React/TypeScript are suitable application choices. DuckDB is for analytics; PostgreSQL is for shared mutable control state. Sarvam is optional assistance. These choices do not grant AI authority over calculations, permission or actions.

## Important behavior

- Authorize before querying or reusing an answer.
- Reuse only compatible identity/scope, date and data/definition versions.
- Keep queue, cache, investigation and provider resource use bounded.
- Treat a complete brief separately from a pending human approval.
- Save the evidence and exact proposal needed for a later decision.
- Recheck the full approved/edited proposal and current evidence before execution.
- Return explicit partial, failed, rejected, expired and non-executed outcomes.
- Keep business audit separate from diagnostic traces.
- Do not call simulated execution operational incident resolution.

## Data rules

The tenant is business_unit. Every trip-level join and aggregate uses (business_unit, trip_id). Metric definitions and exclusion rules come from the [approved data and metric rules](team-handbook/data-and-metric-rules.md). Current values are contextualized using the approved reference window, qualified peers or visibly configured targets.

Do not invent GPS, driver IDs, contractual SLAs, budgets or named-team mappings. The source fields product_type, shift_type, office and trip_direction map to mode, shift_id, site_id and direction.

## Recovery and scale

An identifier for dataset content is not a publication protocol. A saved analysis result is not a complete ingestion snapshot. Plan and test recovery separately for completed results, pending approvals and interrupted computations.

Keep one process responsible for an active analytical database file, or publish immutable analytical files for independent readers. Use PostgreSQL for concurrently accessed jobs, approvals and audit. A file queue with atomic rename alone does not make multiple state changes transactional.

Measure fresh calculations, reused results and provider-assisted requests separately. Increasing worker counts does not establish lower latency. Include queue saturation and approval response time while background work is active.

## First delivery and later additions

First deliver a complete evidence-to-approval journey with one audited simulated effect, deterministic fallback, the primary golden case and safe degraded-data behavior.

Proposed additions are immutable dataset publication with last-good rollback, permission-filtered quality provenance and incident follow-up across runs. Each needs its own records, owner and acceptance check. Incident closure requires evidence or a human decision.

## Team references

- [All agents, nodes and components in plain English](team-handbook/plain-english-build-guide.md)
- [Assignable work packages and dependencies](team-handbook/team-work-packages.md)
- [Data and metric rules](team-handbook/data-and-metric-rules.md)
- [Architecture illustration](high-level-design-visual.html)
