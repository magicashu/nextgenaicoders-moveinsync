# Evaluation corpus

Deterministic gates first; an LLM judge is used only for explanation clarity and never for arithmetic,
authorization, evidence support or action correctness (D-011, D-033).

| Folder | Content | Gate |
|---|---|---|
| `golden/` | G1, G2, G3 trajectory/evidence expectations and the ten hand-reconciled metric fixtures | `QualityGateTest`, `MetricFixtureGateTest`, `GoldenTrajectoryGateTest` |
| `adversarial/` | cross-tenant, prompt injection, forged tool instructions, unsupported claims, approval bypass | `AdversarialCorpusTest`, `scripts/demo/scorecard.sh` |
| `recovery/` | timeout, retry exhaustion, partial branch failure, malformed model output, crash/resume, duplicate effect | `RecoveryCorpusTest` |
| `corrupted/` | generator for V1-V5 degraded copies of the official files | `generate_variants.py` (offline, never touches originals) |
| `expected/` | scorecard schema and pass criteria | `ScorecardTest` |
| `results/` | git-ignored artifacts written by `scripts/demo/scorecard.sh` against a running API | consumed by the gated tests |

Runtime budgets: CI unit gates ≤ 8 minutes, evaluation gates ≤ 15 minutes, demo smoke ≤ 60 seconds.
