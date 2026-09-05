# Agent 4 Implementation Specification: Briefing and Action Drafting

**Owner:** WS2/WS4 workflow and reporting integration  
**Workflow node:** `COMPOSE_DECISION_BRIEF` (14), followed by the deterministic `ACTION_POLICY_GATE` (15)  
**Status:** implementation specification for the Java scaffold  
**Authority:** `docs/requirement.md`, `docs/agents-guide.md`, `docs/architecture.md`

## 1. Purpose and boundary

Agent 4 turns the Evidence Critic's **deterministically verified** claims into two audience views and, when policy permits, a draft action proposal. It is a presentation and bounded drafting role. It does not discover facts, calculate metrics, verify claims, authorize access, approve actions, revalidate evidence, execute effects, or contact external entities.

The single source of factual truth is the `VerificationResult` received from Agent 3. Agent 4 may select and order claim identifiers for relevance, but fixed templates and deterministic services own wording, values, caveats, scope, policy, status, and expiry.

### Ownership boundaries

| Area | Agent 4 owns | Agent 4 must not own |
|---|---|---|
| Agent 1 / Supervisor | Consume its selected issue context already present in `RunContext` | Plan investigations, preserve/alter required comparisons, choose workers, set thresholds |
| Agent 2 / Investigator | Consume completed task evidence indirectly through verified claims | Query workers, DuckDB, SQL, source CSVs, or analytical repositories |
| Agent 3 / Evidence Critic | Consume only `VerificationResult.claims` and its warnings/rejected IDs | Accept candidate claims, repair citations, fetch evidence, reinterpret rejected claims |
| WS3 access/action/approval | Emit a policy-checkable draft | Authorize tenants, approve, edit, revalidate, execute, deduplicate effects, audit execution |
| WS4 reporting | Provide structured brief data to the renderer | Recalculate facts in HTML/UI or introduce a second factual narrative |

No Agent 4 code may depend on DuckDB repositories, metric calculators, investigation workers, provider tools, vendor adapters, approval executors, or external communication clients.

## 2. Exact implementation locations

The current scaffold already exposes the primary seam:

```text
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/BriefingActionAgent.java
backend/src/main/java/com/moveinsync/mobilitycopilot/reporting/domain/DecisionBrief.java
backend/src/main/java/com/moveinsync/mobilitycopilot/action/domain/ActionProposal.java
backend/src/main/java/com/moveinsync/mobilitycopilot/action/domain/ActionTarget.java
backend/src/main/java/com/moveinsync/mobilitycopilot/evidence/domain/VerificationResult.java
backend/src/main/java/com/moveinsync/mobilitycopilot/evidence/domain/VerifiedClaim.java
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/domain/RunContext.java
backend/src/main/java/com/moveinsync/mobilitycopilot/reporting/application/BriefRenderer.java
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/adapter/statemachine/DeterministicWorkflowEngine.java
```

Implement or extend only these Agent 4 surfaces:

```text
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/BriefingActionAgent.java
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/agents/DeterministicBriefingActionAgent.java       # new implementation
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/application/BriefingComposer.java              # new port/use case, optional
backend/src/main/java/com/moveinsync/mobilitycopilot/reporting/domain/BriefAudience.java                   # new enum
backend/src/main/java/com/moveinsync/mobilitycopilot/reporting/domain/AudienceProfile.java                  # new record
backend/src/main/java/com/moveinsync/mobilitycopilot/action/domain/AllowedActionPolicy.java                # new input DTO or WS3-owned equivalent
backend/src/main/java/com/moveinsync/mobilitycopilot/reporting/adapter/DeterministicBriefTemplateRenderer.java # new
backend/src/test/java/com/moveinsync/mobilitycopilot/workflow/agents/DeterministicBriefingActionAgentTest.java
backend/src/test/java/com/moveinsync/mobilitycopilot/reporting/BriefTemplateContractTest.java
backend/src/test/java/com/moveinsync/mobilitycopilot/action/ActionDraftGuardrailTest.java
```

The exact package ownership must be agreed with the integration owner before changing shared records. The existing `DecisionBrief` and JSON schemas remain the public handoff shape unless a reviewed versioned contract change is made.

Related contract files to update only when fields are added:

```text
contracts/schemas/team-contracts.schema.json
contracts/schemas/decision-brief.schema.json
contracts/schemas/action-proposal.schema.json
```

Do not change metric SQL, ingestion, evidence verification, approval, execution, or frontend calculation code for this role.

## 3. Inputs

### 3.1 Required runtime input

The existing Java seam is:

```java
DecisionBrief draft(RunContext context, VerificationResult verifiedEvidence);
```

The implementation must treat these values as immutable input:

```java
record VerificationResult(
    Status status,                 // VERIFIED, QUALIFIED, or REJECTED
    List<VerifiedClaim> claims,    // only claims accepted by Agent 3/fixed verifier
    Set<String> rejectedClaimIds,
    List<String> warnings          // material limitations and verification warnings
) {}

record VerifiedClaim(
    String claimId,
    TenantContext tenant,
    String dataVersion,
    String metricVersion,
    String text,
    Set<String> evidenceIds,
    Kind kind                     // DIRECT or QUALIFIED_INFERENCE
) {}
```

`RunContext` supplies the trusted run identity, actor, tenant, persona, as-of date, version bundle, budget, and deadline. Agent 4 must reject or fail closed if a claim tenant, data version, or metric version conflicts with the context. It must never repair the mismatch by changing context.

### 3.2 Audience profiles

Use two fixed profiles. They control emphasis and ordering only; they do not authorize different facts or permit different values.

```java
public enum BriefAudience { OPERATIONS, LEADERSHIP }

public record AudienceProfile(
    BriefAudience audience,
    String titleTemplateId,
    List<String> preferredClaimKinds,
    List<String> requiredSections,
    int maxClaims
) {}
```

Required profiles:

| Audience | Purpose | Required content |
|---|---|---|
| `OPERATIONS` | Help a transport manager decide what to inspect next | issue, affected authorized scope, contributing verified claims, operational limitations, bounded next review/action status |
| `LEADERSHIP` | Provide a concise forwardable report | period/as-of date, scope, decision-relevant findings, caveats, methodology/evidence references, proposal status |

The profiles must reference claim IDs, not copied or independently generated measurements.

### 3.3 Material caveats

Caveats are the union of `VerificationResult.warnings`, source/evidence warnings already represented by the verified input, and deterministic status caveats supplied by the caller. The implementation must preserve their provenance and classify them at least as:

```java
public record MaterialCaveat(
    String caveatId,
    String text,
    String severity,              // INFO, LIMITATION, DATA_QUALITY, UNAVAILABLE
    Set<String> evidenceIds,
    boolean blocksOperationalEscalation
) {}
```

A `DATA_QUALITY` or `UNAVAILABLE` caveat must be visible in both views when it affects an included claim. `blocksOperationalEscalation=true` prevents a consequential action draft; it must not be converted into an operational-health claim.

### 3.4 Allowed action policies

Action policies are deterministic input supplied by WS3/action policy, not selected by the model.

```java
public record AllowedActionPolicy(
    String policyVersion,
    Set<String> allowedTypes,
    Set<String> allowedDimensionKeys,
    Set<String> requiredEvidenceKinds,
    Duration proposalLifetime,
    boolean externalCommunicationAllowed, // false for this prototype
    boolean mockExecutionOnly               // true for this prototype
) {}
```

The default prototype policy must allow mock/draft action types only, require human approval, and set `externalCommunicationAllowed=false`. Agent 4 may propose no action when the policy is absent, ambiguous, unsupported, or contradicted by a material caveat.

## 4. Processing pipeline

The implementation must make the deterministic and AI paths visibly separate.

```text
VerificationResult + RunContext + profiles + caveats + policy
        |
        v
[1] deterministic input/scope/version validation
        |
        v
[2] deterministic eligible-claim set (verified claims only)
        |
        +--> [3A] optional AI selector: claim IDs and ordering only
        |          bounded output: {claimId, rank, reasonCode}
        |
        +--> [3B] deterministic ordering fallback
        |
        v
[4] fixed templates render claim text/value references and caveats
        |
        v
[5] deterministic action-draft eligibility, target/scope, evidence, expiry
        |
        v
DecisionBrief(status = draft-only for proposals)
```

### Step 1: Validate inputs

Reject or produce a qualified report when:

- `VerificationResult.status == REJECTED` or contains no usable claims.
- Any claim has no evidence ID, no tenant, no data version, or no metric version.
- A claim is rejected, missing from `claims`, or not in the verifier-approved set.
- A claim's tenant/version conflicts with `RunContext`.
- The input includes an unsupported audience or action policy.

Never silently discard a material conflict. Put the reason in `caveats` and suppress the affected claim/action.

### Step 2: Build the eligible claim set

Only `VerificationResult.claims` are eligible. `rejectedClaimIds`, candidate `Claim` records, raw evidence, warnings, model text, and issue labels are not claim sources. Deduplicate by `claimId`; duplicate IDs with different content fail closed.

Retain the original claim text and evidence IDs for traceability. Agent 4 may attach a template label or section, but may not rewrite the claim's metric meaning.

### Step 3: Select and order claims

The optional AI adapter may receive compact metadata only:

```json
{
  "audience": "OPERATIONS",
  "eligibleClaimIds": ["claim-..."],
  "claimKinds": {"claim-...": "DIRECT"},
  "caveatIds": ["quality-..."],
  "allowedSectionIds": ["issue", "contributors", "limitations", "next-step"]
}
```

It may return only:

```json
{
  "selectedClaimIds": ["claim-..."],
  "order": [{"claimId": "claim-...", "rank": 1, "reasonCode": "PRIMARY_FINDING"}]
}
```

Validate the response deterministically: schema, maximum count, unique IDs, membership in the eligible set, no invented section, and no unsupported priority reason. Ignore invalid AI output and use the fallback. Do not send raw tables, source rows, secrets, or unnecessary rider/vendor identities to the provider.

Recommended deterministic ordering:

1. direct claims relevant to the selected issue;
2. qualified inferences with explicit qualification;
3. claims that explain concentration/comparison;
4. claims needed to understand action scope;
5. stable `claimId` order as the final tie-breaker.

The same selected claim IDs must back both audience views. Audience ordering may differ, but audience-specific facts may not.

### Step 4: Render with fixed templates

AI does not generate final prose. Templates interpolate only validated fields and the original verified claim text. Templates must escape output for the target renderer and preserve evidence references.

Example template contract:

```text
TITLE[OPERATIONS] = "Operations decision brief — {tenant} — {asOfDate}"
TITLE[LEADERSHIP] = "Leadership summary — {tenant} — {period}"

FINDING = "{verifiedClaim.text} [evidence: {evidenceIds}]"
CAVEAT = "Limitation: {caveat.text}"
UNAVAILABLE = "Unavailable: {caveat.text}; no value is substituted."
ACTION = "Draft for review only: {proposal.title}; approval is required."
```

A template may add connective words such as “The verified evidence shows”. It may not add a number, unit, denominator, target, vendor attribution, causal explanation, or comparison absent from a verified claim. All material caveats are rendered before the proposal section.

### Step 5: Draft action proposal

Action drafting is deterministic and subordinate to the policy gate. Agent 4 may construct a proposal object for WS3 validation, but it must set:

```text
status = DRAFT
proposalVersion = 1 for a new proposal
runId = RunContext.runId
 target.tenant = RunContext.tenant
 dataVersion/metricVersion = context versions represented by all cited claims
 evidenceIds = union of cited verified claim evidence IDs
 createdAt = current UTC application time
 expiresAt = createdAt + policy.proposalLifetime
```

The target may contain only the authorized tenant and allowlisted dimensions already present in verified evidence or the selected issue scope. It must not invent a vendor, site, shift, direction, trip, threshold, budget, savings value, or external recipient.

Before returning a proposal, verify:

- action type is in `AllowedActionPolicy.allowedTypes`;
- all parameters are allowlisted and non-empty;
- target tenant equals the run tenant;
- every evidence ID belongs to an included verified claim;
- the proposal has an explicit future expiry timestamp;
- any data-quality/unavailable caveat that blocks escalation suppresses the proposal;
- `externalCommunicationAllowed` is false and no external contact is represented;
- status is exactly draft/pending review according to the shared enum, never approved or executable.

When any check fails, return a report-only brief with an empty `proposedActions` list and a visible caveat. An empty action list is valid and preferred to a weak proposal.

## 5. Output contracts

### 5.1 Decision brief

The existing Java record is the primary handoff:

```java
public record DecisionBrief(
    RunContext context,
    String operationalSummary,
    String leadershipSummary,
    VerificationResult verification,
    List<ActionProposal> proposedActions,
    List<String> caveats
) {}
```

Both summaries must:

- use the same `VerificationResult` and same verified claim IDs;
- state tenant/scope and reporting/as-of context when available;
- preserve direct versus qualified-inference meaning;
- include applicable data-quality, low-coverage, partial, and unavailable caveats;
- include evidence IDs or links to the evidence drawer;
- state “draft for approval” for every proposal;
- avoid claiming execution, incident resolution, causality, savings, SLA compliance, or vendor contact.

The leadership renderer at `reporting/application/BriefRenderer.java` is responsible for printable HTML escaping and layout. It must not calculate or add facts. The browser receives the structured brief and renders it; it is not a fact source.

### 5.2 Action proposal

Use the existing `ActionProposal`/`ActionTarget` shape and `contracts/schemas/action-proposal.schema.json`:

```java
public record ActionProposal(
    UUID actionId,
    UUID runId,
    long proposalVersion,
    String type,
    String title,
    String rationale,
    String status,
    ActionTarget target,
    String dataVersion,
    String metricVersion,
    Set<String> evidenceIds,
    Map<String, String> parameters,
    Instant createdAt,
    Instant expiresAt
) {}
```

The rationale is a template composition of verified claim IDs/text and caveats. It is not an AI-generated causal explanation. The proposal is an input to `ACTION_POLICY_GATE`; constructing this record is not approval and must not trigger an execution API.

### 5.3 Failure output

Provider timeout, malformed selection, missing policy, unsupported capability, or template failure must produce a deterministic report where possible:

```json
{
  "operationalSummary": "The verified evidence is available, but automated claim ordering was unavailable; the deterministic brief is shown.",
  "leadershipSummary": "The verified evidence is summarized using the fixed report template.",
  "proposedActions": [],
  "caveats": ["AI briefing assistance unavailable; no facts were added."]
}
```

The fallback must preserve all eligible verified claims and material caveats subject to fixed size limits. It must never turn an unavailable metric into zero or a failed provider call into a healthy result.

## 6. Strict must-not rules

Agent 4 must never:

1. add raw numbers, percentages, units, denominators, dates, targets, thresholds, or names not present in verified claims/context;
2. cite candidate/rejected claims, raw evidence, SQL results, source files, or model statements as verified facts;
3. omit critical data-quality, low-coverage, partial, unavailable, regime-change, or unsupported-metric caveats;
4. silently change a metric definition, population, comparison period, scope, unit, or causal strength;
5. convert a recording/data-quality note into an operational escalation;
6. invent a root cause or blame a vendor from correlation or a single contribution;
7. query database, DuckDB, SQL, ingestion, metric, or investigation layers directly;
8. call approval, revalidation, mock execution, vendor, employee, email, ticketing, or other downstream/external APIs;
9. approve, execute, mark effective, or claim resolution for an action;
10. widen tenant scope or use `trip_id` without the `(business_unit, trip_id)` tenant-qualified identity;
11. use audience selection to show different facts or hide a caveat;
12. treat a fluent AI response as evidence or allow AI to choose action policy, action state, target, expiry, or permissions.

## 7. Deterministic fallback versus AI orchestration

### Deterministic path (mandatory)

Implemented in `DeterministicBriefingActionAgent` and fixed template services:

- validate scope, versions, claim IDs, evidence references, caveats, and policy;
- select/order claims with stable rules;
- render both summaries;
- suppress unsafe claims and all ineligible proposals;
- produce exact draft status, tenant-qualified target, evidence set, versions, and expiry;
- expose provider/fallback status in caveats/observability.

This path must work with no provider key, no network, no database access from Agent 4, and no PostgreSQL instance. It is the release baseline.

### Optional AI path

Implemented behind the existing server-side provider boundary under:

```text
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/adapter/sarvam/
backend/src/main/java/com/moveinsync/mobilitycopilot/workflow/application/ports/
```

The provider may rank eligible claim IDs and choose section ordering. It must use bounded timeouts, schema validation, redacted compact inputs, versioned prompt/model metadata, and at most the shared correction/retry allowance. It must never render final values or control action state. A timeout or invalid response immediately falls back to deterministic ordering; it does not retry indefinitely.

## 8. Acceptance gates and tests

Tests must align with the official G1, G2, and G3 stories and the broader acceptance criteria in `docs/requirement.md`.

### G1: concentration without unsupported blame

Given verified site/shift concentration claims and evidence showing no supported single-vendor conclusion:

- both views include the concentration claim and evidence IDs;
- neither view adds vendor blame;
- any proposal is scoped only to the verified authorized dimension;
- the output remains draft-only and contains no execution claim.

### G2: explicit coverage and unavailable metrics

Given low feedback coverage or an unsupported cost-per-km metric:

- the relevant caveat appears in both views;
- unavailable is rendered as unavailable, never zero or a guessed value;
- no unsupported savings or cost recommendation is drafted;
- claim text, unit, population, and evidence references remain unchanged.

### G3: data-quality note does not escalate

Given a tracking/recording regime change or other data-quality note:

- the note is visible in operations and leadership summaries;
- the output does not call it an operational incident or recommend escalation;
- `proposedActions` is empty when policy marks the caveat as escalation-blocking;
- the fallback path produces the same safe result without AI.

### Required unit and contract tests

```text
DeterministicBriefingActionAgentTest
- rejects mismatched tenant/version claims
- excludes rejected and duplicate claim IDs
- preserves direct versus qualified-inference wording
- renders the same claim IDs in both audience views
- retains all material caveats
- renders unavailable without substituting zero
- ignores malformed/out-of-set AI ordering
- falls back on timeout/provider absence
- emits draft-only proposal with evidence, tenant scope, versions and future expiry
- suppresses proposal for escalation-blocking data-quality caveat
- never calls a downstream action/approval client

BriefTemplateContractTest
- validates output against decision-brief/action-proposal schemas
- verifies evidence IDs are present for every included finding
- verifies HTML/string escaping in the printable renderer
- verifies no generated numeric token lacks a verified source

ActionDraftGuardrailTest
- rejects foreign tenant and unallowlisted dimensions
- rejects missing/expired/non-future expiry
- rejects unknown action type and external communication
- rejects APPROVED/EXECUTED status from Agent 4
- verifies empty proposedActions is accepted for report-only outcomes
```

Deterministic assertions, not a model judge, decide metric/value support, tenant isolation, caveat presence, draft status, expiry, and action behavior. Semantic review may assess readability only after the deterministic gates pass.

## 9. Observability and handoff evidence

Record a redacted composition event containing `runId`, tenant, audience profile IDs, eligible/selected claim IDs, caveat IDs, template version, provider attempt/fallback status, model/prompt versions when applicable, duration, and proposal ID. Do not log raw rider identities, secrets, full source rows, or provider keys.

The Agent 4 handoff to the integration owner must include:

```text
implemented classes and exact paths
input/output contract version
list of template IDs and wording rules
AI adapter schema and fallback behavior
G1/G2/G3 test evidence
known unsupported action types and limitations
confirmation that no database or downstream execution dependency was added
```

A completed Agent 4 implementation is ready only when it can produce faithful dual summaries and a safe deterministic fallback from `VerificationResult` alone, while every action remains an explicitly expiring draft awaiting the separate WS3 policy, approval, revalidation, mock execution, and audit path.
