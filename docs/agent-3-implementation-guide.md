# Agent 3 — Evidence Critic Implementation Guide

## Objective

Implement **Agent 3 (Evidence Critic)** in the existing Spring Boot backend.

The implementation must fit the existing project architecture and reuse existing interfaces/domain objects wherever possible.

Initial LLM provider:

- **Ollama**
- **Gemma 3 4B** for local validation

Future mandatory hackathon provider:

- **Sarvam API**

The LLM provider must therefore be replaceable without changing Agent 3 business logic.

---

## 1. Existing Architecture to Reuse

The backend already contains the following relevant concepts:

```text
evidence/
├── application/
│   └── EvidenceVerifier.java
│
└── domain/
    ├── Claim.java
    ├── MetricEvidence.java
    ├── VerifiedClaim.java
    └── VerificationResult.java

workflow/
├── agents/
│   └── EvidenceCriticAgent.java
│
├── application/
│   └── ports/
│       └── LanguageModelPort.java
│
└── adapter/
    ├── langgraph4j/
    ├── sarvam/
    └── statemachine/
```

Do NOT create a parallel agent architecture.

Reuse the existing:

- `EvidenceCriticAgent`
- `LanguageModelPort`
- `Claim`
- `MetricEvidence`
- `VerifiedClaim`
- `VerificationResult`
- `EvidenceVerifier`
- `RunContext`
- existing workflow/state-machine contracts

Inspect the actual source before modifying anything.

---

# 2. Agent 3 Responsibility

Agent 3 sits after investigation/evidence merging and before briefing/action generation.

```text
Investigation
    ↓
Evidence Merge
    ↓
Agent 3 — Evidence Critic
    ↓
Deterministic Evidence Verification
    ↓
Verified Claims
    ↓
Agent 4 / Briefing
```

Agent 3 must:

- challenge unsupported claims
- identify unsupported vendor attribution
- identify unsupported causality
- identify contradictions
- identify missing comparisons
- identify missing caveats
- identify insufficient evidence
- identify invalid or mismatched evidence references

Agent 3 must NOT:

- fetch additional data
- generate SQL
- invent facts
- invent evidence
- calculate authoritative metrics
- change tenant/scope/period
- override deterministic verification
- make authorization decisions
- approve operational actions

---

# 3. Recommended Implementation Structure

Add only the minimum new classes required.

```text
evidence/
├── application/
│   └── EvidenceVerifier.java
│
└── domain/
    ├── Claim.java
    ├── MetricEvidence.java
    ├── VerifiedClaim.java
    ├── VerificationResult.java
    ├── CritiqueResult.java          NEW
    └── CritiqueIssue.java           NEW

workflow/
├── agents/
│   ├── EvidenceCriticAgent.java
│   └── EvidenceCriticAgentImpl.java NEW
│
├── application/
│   └── ports/
│       └── LanguageModelPort.java
│
└── adapter/
    ├── ollama/
    │   └── OllamaLanguageModelAdapter.java NEW
    │
    └── sarvam/
        └── SarvamLanguageModelAdapter.java   LATER
```

Do not introduce another `EvidenceCriticService` if `EvidenceCriticAgent` is already the workflow-facing abstraction.

---

# 4. Agent 3 Flow

Implement the following flow:

```text
EvidenceCriticAgentImpl.review()
        │
        ├── 1. Build candidate claims
        │
        ├── 2. Build LLM ModelRequest
        │
        ├── 3. Call LanguageModelPort
        │       └── Ollama/Gemma initially
        │
        ├── 4. Parse structured critique
        │
        └── 5. Pass claims/evidence through EvidenceVerifier
                    │
                    ↓
             VerificationResult
```

The LLM is an untrusted semantic critic.

The deterministic verifier is the final authority for evidence validation.

---

# 5. Domain Objects

Existing `Claim` should remain the core candidate-claim representation.

Expected existing shape:

```java
public record Claim(
    String claimId,
    String text,
    Set<String> evidenceIds,
    VerifiedClaim.Kind kind
) {}
```

Do not unnecessarily redesign this class.

Add a small `CritiqueResult` representing the model's critique.

Suggested shape:

```java
public record CritiqueResult(
    String overallStatus,
    List<ClaimReview> claims,
    List<String> globalCaveats
) {}
```

Suggested `ClaimReview`:

```java
public record ClaimReview(
    String claimId,
    String decision,
    List<CritiqueIssue> issues,
    List<String> requiredCaveats
) {}
```

Suggested `CritiqueIssue`:

```java
public record CritiqueIssue(
    String type,
    String severity,
    String explanation
) {}
```

Use enums if the existing project conventions support them.

Recommended issue types:

```text
UNSUPPORTED_CLAIM
UNSUPPORTED_ATTRIBUTION
UNSUPPORTED_CAUSALITY
MISSING_COMPARISON
MISSING_CAVEAT
CONTRADICTORY_EVIDENCE
INSUFFICIENT_EVIDENCE
INVALID_REFERENCE
SCOPE_MISMATCH
DATA_VERSION_MISMATCH
```

Keep this model small.

The critique result must never be treated as verified evidence.

---

# 6. LLM Provider Abstraction

Reuse the existing:

```java
LanguageModelPort
```

Do not create a second provider interface.

Create:

```text
workflow/adapter/ollama/OllamaLanguageModelAdapter.java
```

which implements `LanguageModelPort`.

Later create:

```text
workflow/adapter/sarvam/SarvamLanguageModelAdapter.java
```

which also implements `LanguageModelPort`.

The architecture should therefore be:

```text
                 LanguageModelPort
                       ▲
                       │
             ┌─────────┴─────────┐
             │                   │
       Ollama Adapter       Sarvam Adapter
          Gemma                 Sarvam
```

Agent 3 must depend only on `LanguageModelPort`.

---

# 7. Ollama Configuration

Add configuration following the project's existing configuration conventions.

Conceptually:

```yaml
ai:
  provider: ollama

  ollama:
    base-url: http://localhost:11434
    model: gemma3:4b
    temperature: 0.1
    timeout: 30s
```

Do not hard-code:

- model name
- base URL
- temperature
- timeout

Use the project's existing configuration/binding conventions if available.

Use Spring `RestClient` or the HTTP client already established by the project.

Do not add a heavy AI framework solely for this integration.

---

# 8. Ollama Request

Use Ollama's chat API.

The model should receive:

- system prompt
- candidate claims
- compact evidence
- relevant run context
- requested tenant/period/scope where appropriate

Do NOT send raw CSV/table contents to the model.

Do NOT give the model database or SQL access.

Evidence should already be reduced to governed `MetricEvidence` objects.

---

# 9. Structured Output

The model must return structured JSON.

Do not rely on free-form prose parsing.

Conceptual response:

```json
{
  "overallStatus": "NEEDS_CORRECTION",
  "claims": [
    {
      "claimId": "C001",
      "decision": "CORRECT",
      "issues": [
        {
          "type": "UNSUPPORTED_ATTRIBUTION",
          "severity": "HIGH",
          "explanation": "The evidence shows Vendor ABC deteriorated, but does not establish that Vendor ABC caused the overall increase."
        }
      ],
      "requiredCaveats": [
        "Other qualified vendors also deteriorated."
      ]
    }
  ],
  "globalCaveats": []
}
```

Prefer JSON Schema / structured output if supported by the configured model/provider.

The same logical response contract should be usable when switching from Ollama to Sarvam.

---

# 10. Agent 3 System Prompt

Use a restrictive prompt similar to:

```text
You are the Evidence Critic in an enterprise mobility analytics system.

Your job is to challenge candidate conclusions before they are shown
to a transport manager.

Evaluate ONLY the supplied claims and evidence.

You MUST:
1. Identify unsupported claims.
2. Identify unsupported vendor attribution.
3. Identify unsupported causal claims.
4. Identify contradictions.
5. Identify missing comparisons.
6. Identify missing data-quality caveats.
7. Identify insufficient evidence.
8. Identify invalid evidence references.
9. Return only structured JSON matching the supplied schema.

You MUST NOT:
1. Invent facts.
2. Invent evidence.
3. Fetch additional data.
4. Generate SQL.
5. Calculate authoritative metrics.
6. Change tenant, scope, or reporting period.
7. Approve actions.
8. Treat correlation as causation.

A vendor may be identified as a contributor only when the supplied
evidence supports that interpretation.

Do not claim that a vendor caused an overall mobility issue unless
the supplied evidence establishes that conclusion.

If multiple qualified vendors deteriorated, flag unsupported
single-vendor attribution.

Treat missing or low-coverage data as a caveat, not as zero.

Return ONLY JSON.
```

Keep temperature low, approximately `0.1`.

---

# 11. Deterministic Verification

`EvidenceVerifier` must remain the final authority.

Verify at minimum:

```text
✓ evidence reference exists
✓ tenant matches
✓ reporting period matches
✓ metric matches
✓ metric version matches
✓ data version matches
✓ unit matches
✓ cited value exists
✓ numerator/denominator are consistent when available
✓ required comparison exists
✓ evidence scope is correct
```

If evidence cannot establish a claim, prefer:

```text
INSUFFICIENT_EVIDENCE
```

or:

```text
REJECT
```

rather than silently accepting the claim.

The LLM cannot override these checks.

---

# 12. Critical Golden Test

Agent 3 must correctly challenge unsupported vendor blame.

Example candidate claim:

```text
Vendor ABC caused the increase in delays.
```

Evidence:

```text
Vendor ABC
Current delay rate: 24%
Previous delay rate: 10%

Vendor XYZ
Current delay rate: 21%
Previous delay rate: 9%

Vendor PQR
Current delay rate: 19%
Previous delay rate: 8%
```

Expected critique:

```text
NEEDS_CORRECTION

The evidence supports that Vendor ABC deteriorated,
but does not support attributing the overall increase
to Vendor ABC alone.

Other qualified vendors also deteriorated.
```

This should be a mandatory integration/golden test.

---

# 13. Other Required Test Cases

Create tests for at least:

1. Supported factual claim
2. Supported contributor claim
3. Unsupported single-vendor attribution
4. Unsupported causal claim
5. Multiple vendors deteriorating
6. Missing evidence
7. Invalid evidence ID
8. Wrong tenant
9. Wrong reporting period
10. Metric version mismatch
11. Data version mismatch
12. Missing comparison
13. Low-coverage evidence
14. Contradictory evidence
15. Ollama unavailable
16. Malformed LLM JSON

For LLM tests, avoid making every test dependent on a running Ollama instance.

Separate:

- deterministic unit tests
- model adapter tests
- Agent 3 integration tests

---

# 14. Failure Handling

If Ollama is unavailable or times out:

```text
Ollama failure
      ↓
Deterministic verification
      ↓
Safe result
```

Do not mark unverified claims as accepted simply because the model is unavailable.

When semantic critique cannot be performed, preserve the deterministic result and clearly indicate that semantic critique was unavailable if the existing result contract supports metadata/status.

If the model returns malformed JSON:

- do not attempt unsafe free-form interpretation
- record the model failure
- fall back to deterministic verification
- avoid accepting unsupported claims

---

# 15. Sarvam Migration

Do not implement Sarvam business logic inside Agent 3.

Later add:

```text
SarvamLanguageModelAdapter
        implements LanguageModelPort
```

Configuration changes from:

```yaml
ai:
  provider: ollama
```

to:

```yaml
ai:
  provider: sarvam
```

Agent 3 should remain unchanged.

The following must NOT change when switching providers:

- `Claim`
- `MetricEvidence`
- `VerificationResult`
- `EvidenceVerifier`
- Agent 3 business logic
- workflow/state-machine contracts
- public evidence/fact contracts

Only the model adapter/configuration should change.

---

# 16. Implementation Constraints

Before coding:

1. Inspect the actual existing Java classes.
2. Reuse existing package names.
3. Reuse existing DTOs/interfaces wherever possible.
4. Follow existing naming/style conventions.
5. Do not duplicate existing models.
6. Do not introduce unnecessary frameworks.
7. Do not modify unrelated agents.
8. Do not modify Agent 4 unless required to compile/integrate.
9. Keep the implementation focused on Agent 3.
10. Keep the Ollama dependency isolated behind `LanguageModelPort`.

---

# 17. Definition of Done

Agent 3 is complete when:

```text
✓ EvidenceCriticAgent implementation exists
✓ Existing LanguageModelPort is reused
✓ Ollama/Gemma adapter works locally
✓ Structured JSON critique works
✓ Candidate claims are reviewed
✓ EvidenceVerifier performs deterministic checks
✓ Unsupported vendor blame is rejected/corrected
✓ Unsupported causality is challenged
✓ Missing evidence is handled safely
✓ Tenant/period/version mismatches are rejected
✓ Ollama failure has safe fallback
✓ Unit tests cover deterministic verification
✓ Integration test demonstrates Gemma → critique → verification
✓ No raw database/CSV access is exposed to the LLM
✓ Sarvam can later be implemented as another LanguageModelPort adapter
✓ Existing workflow contracts remain intact
```

---

## Recommended implementation order

Implement in this order:

1. Inspect existing `EvidenceCriticAgent`, `LanguageModelPort`, `EvidenceVerifier`, and domain classes.
2. Implement/complete deterministic `EvidenceVerifier`.
3. Add `CritiqueResult` / `CritiqueIssue` only if no equivalent existing types exist.
4. Implement `EvidenceCriticAgentImpl`.
5. Implement `OllamaLanguageModelAdapter`.
6. Add structured Gemma prompt/schema.
7. Add golden tests.
8. Add Ollama integration test.
9. Verify failure/fallback behavior.
10. Only after Agent 3 is stable, implement the Sarvam adapter.

Do not over-engineer Agent 3. The desired architecture is:

```text
EvidenceCriticAgent
        ↓
LanguageModelPort
        ↓
Gemma / Sarvam
        ↓
CritiqueResult
        ↓
EvidenceVerifier
        ↓
VerificationResult
```
