# Agent 3 — Evidence Critic

## Purpose

Agent 3 reviews candidate claims after governed investigation evidence is merged
and before a briefing or proposal is produced. It protects the decision path by
challenging weak interpretations; it never creates operational facts.

```text
Governed investigation evidence
            ↓
Candidate claims + caveats
            ↓
Agent 3 semantic critique (optional model)
            ↓
Deterministic evidence verification (authority)
            ↓
Verified / qualified / rejected claims
            ↓
Briefing and action-policy stages
```

## Responsibility boundary

Agent 3 must identify:

- Unsupported factual claims or evidence references.
- Unsupported vendor attribution, especially single-vendor blame.
- Unsupported causal language.
- Scope, period, unit, metric, data-version, or metric-version mismatches.
- Missing current/reference comparisons.
- Contradictory evidence, insufficient evidence, and missing data-quality caveats.

Agent 3 must not fetch data, access CSV files, generate SQL, calculate a
governed metric, change a tenant/window/filter, authorize data access, approve
an action, or invent evidence. Correlation is not causation.

## Project contracts used

| Contract | Role in Agent 3 |
|---|---|
| `Claim` | Candidate text, claim kind, and cited evidence IDs. Never render it as a verified fact. |
| `MetricEvidence` | Compact, governed evidence carrying request scope, value, unit, population, versions, source reference, and warnings. |
| `VerificationResult` | Final verification result; contains accepted claims, rejections, and warnings. |
| `EvidenceVerifier` | Deterministic final authority. Model output cannot override it. |
| `LanguageModelPort` | Optional provider abstraction. Agent logic depends on this port, never on Ollama or Sarvam directly. |
| `RunContext` | Tenant, actor, as-of date, versions, and run bounds propagated through the review. |

## Implemented classes

```text
evidence/application/DeterministicEvidenceVerifier.java
evidence/domain/CritiqueIssue.java
evidence/domain/CritiqueResult.java
workflow/agents/EvidenceCriticAgent.java
workflow/agents/EvidenceCriticAgentImpl.java
workflow/adapter/ollama/OllamaLanguageModelAdapter.java
config/OllamaConfiguration.java
config/OllamaProperties.java
config/StructuredJsonConfiguration.java
```

`EvidenceCriticAgentImpl` has an overload that accepts the candidate claims
from the future evidence-merger contract. The original workflow-facing method
is retained for compatibility; until the merger is implemented it has no
candidate claims to verify.

## Deterministic verification rules

Before a claim can be accepted, the verifier checks that every cited evidence
ID exists and that its request matches the run tenant and data version. It also
requires a valid window no later than the run as-of date, compatible metric
version, expected unit, available numeric value, and evidence warnings.

Qualified inferences require at least two cited evidence records: normally the
current measure and the relevant reference/peer measure. Partial evidence and
source warnings remain visible on the result. Causal language and exclusive
single-vendor language are rejected because the supplied metrics establish
association and contribution, not causation.

The verifier still needs evidence-merger support to validate claim text against
specific cited numeric values and to prove each metric-specific comparison
requirement. Do not treat this partial verifier as authorization to render
unbounded claims.

## Dataset integration rules

Agent 3 receives only governed `MetricEvidence`, never raw records. The source
data implementation must have already enforced the official rules:

- Every trip and child join is scoped by `(business_unit, trip_id)`.
- `business_unit` is the tenant and remains in every evidence request.
- Metric values carry their M01–M18 contract version, dataset version, filters,
  window, unit, population, numerator/denominator where applicable, and quality
  warnings.
- Missing and unsupported data are explicit `PARTIAL` or `UNAVAILABLE` states,
  never zero.
- Low feedback coverage, unsupported cost-per-km, suppression/minimum-volume,
  and alert-regime caveats must arrive as evidence warnings.

For G1, vendor evidence can show that a vendor deteriorated, but a claim such
as “Vendor ABC caused the delay increase” must be rejected when other qualified
vendors also deteriorated. The allowed outcome is a qualified contributor or
broader operational finding, backed by site/shift/direction and historical
comparison evidence.

## Optional local provider

The default path has no model dependency. Configure Ollama only for local
semantic critique:

```yaml
mobility:
  ai:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: gemma3:4b
      temperature: 0.1
      timeout: 30s
```

Environment equivalents are `MOBILITY_AI_PROVIDER`, `OLLAMA_BASE_URL`,
`OLLAMA_MODEL`, `OLLAMA_TEMPERATURE`, and `OLLAMA_TIMEOUT`.

The adapter calls Ollama's chat endpoint with JSON output requested. It receives
the restrictive system instruction plus only compact candidate claims and
governed evidence. It has no database, CSV, tool, or action access. A future
Sarvam adapter must implement the same `LanguageModelPort`; Agent 3 business
logic and public evidence contracts must not change.

## Safe failure behaviour

If no provider is configured, Ollama times out, the provider is unavailable, or
its response is malformed JSON, Agent 3 records a semantic-critique limitation
and returns the deterministic verification result. A provider failure never
turns an unsupported claim into an accepted one.

## Tests currently present

- Supported, tenant-scoped factual evidence.
- Missing citation, wrong tenant, and wrong data-version rejection.
- Partial evidence qualification.
- Causal vendor-blame rejection.
- Structured model critique remains advisory.
- Provider failure returns the deterministic result with a warning.

## Remaining Agent 3 work

1. Implement the evidence merger so it creates bounded `Claim` objects and
   invokes the candidate-claim review overload at workflow nodes 12–13.
2. Add deterministic text/value claim matching and metric-specific comparison
   validation after the claim schema is frozen.
3. Add the official G1 integration test using governed vendor and historical
   evidence, including multiple qualifying vendors.
4. Add the G2 caveat and G3 data-regime tests after their metric evidence is
   implemented.
5. Add a real Ollama integration test as optional/local-only; unit and release
   tests must remain provider-independent.
6. Add Sarvam only as another `LanguageModelPort` adapter after its server-side
   configuration and usage controls are available.

## Definition of done

Agent 3 is complete only when the workflow invokes it with official-data
evidence; deterministic verification rejects unsupported references, scope and
version mismatches; G1 rejects single-vendor blame; G2/G3 preserve required
caveats; provider failure is visible and safe; and no model can access raw data
or authorize a decision or action.
