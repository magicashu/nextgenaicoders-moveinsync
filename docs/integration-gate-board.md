# Mobility Decision Copilot — Integration Gate Board

This board is maintained by the Codex Foundation & Integration owner on `Java-branch-2`. A worker packet marked complete is only ready for integration; it is not a release pass. Update the evidence column with a commit and exact command result after every merge.

## Workstream intake

| Workstream | Branch | State | Required handoff evidence |
|---|---|---|---|
| Codex C0 metric/contracts | `Java-branch-2` | Green | `d4aa14d`; contract and serialization tests pass |
| Codex C1 build foundation | `Java-branch-2` | Green | `90c1683`; Java 21 configuration and one-command verification pass |
| Codex C2 application ports | `Java-branch-2` | Green | `90c1683`; control-plane consumer contracts pass |
| Codex C3 Spring composition | `Java-branch-2` | Green | Governed analytics, identity, product API, control plane and telemetry adapters composed; application context and capability report pass |
| Codex official-data gate | `Java-branch-2` | Green | Seven checksums, G1 exact metric reconciliation, full workflow approval/resume and HTTP scorecard pass |
| Claude WS1 governed analytics | `feat/governed-analytics` | Integrated | `54abda6`; seven-file ingest, M01-M18, G1/G2/G3 analytics and 49 packet tests |
| Claude WS2 agent workflow | `feat/agent-workflow` | Integrated | `04a2ec9`; bounded 18-node trajectory, four roles, seven workers and 30 packet tests |
| Claude WS3 governance/actions | `feat/governance-actions` | Integrated | `86d8def`; authorization, checkpoint, approval, revalidation, idempotency, audit and 22+4 packet tests |
| Claude WS4 product API | `feat/product-api` | Integrated | `ea12891`; six product endpoints, dual-audience DTOs and 23 packet tests |
| Claude WS5 React experience | `feat/react-experience` | Integrated | `7bed4c7`; React decision workflow, seven UI tests and production build |
| Claude WS6 quality/telemetry | `feat/quality-telemetry` | Integrated | `401ee3e`; scorecard, adversarial/recovery corpora, redacted trace exporter and 27 packet tests |

## Release gates

| Gate | State | Current evidence / exit condition |
|---|---|---|
| Organizer data integrity | Green | All seven files match `contracts/data/official-checksums.sha256` |
| Build and baseline suite | Green | 127 Java tests, React production build, 7 React tests and fixture validation pass |
| Official G1 M01 | Green | 4,357 / 19,913 = 21.88%; baseline 12.28%; delta 9.60 pp |
| Official HTTP slice | Green | Product API reproduces G1 and reaches `AWAITING_APPROVAL`; smoke approval resumes to `EXECUTED` |
| Complete G1 investigation | Green | M03/M04/M09/M11, site/shift concentration, vendor rebuttal, dual brief and evidence are present |
| G2 honest degradation | Green | Official scorecard passes low-coverage and zero-km caveat gate |
| G3 data-regime suppression | Green | Official scorecard confirms sign-off change is a quality note, not an M13 escalation |
| Tenant isolation | Green | Header, workflow read, approval, governed-tool and composite-key regressions pass |
| Approval and one effect | Green | Approval, fresh revalidation, idempotent mock execution, duplicate rejection and audit tests pass |
| Evidence coverage | Green | Deterministic verifier and scorecard resolve displayed numeric claims to governed evidence IDs |
| Root trace and telemetry fallback | Green | Nested workflow spans, redaction and non-blocking degraded exporter tests pass |
| Browser judge flow | Green | Seven React interaction tests and production build pass against the product API contract |
| Clean-start release and backup | Green | `scripts/release/verify-release.sh` passed twice consecutively: 127 Java tests, 7 React tests/build, exact official metrics, fixture and official HTTP, approval execution, G1/G2/G3, security/audit and 7 generated-artifact evaluator tests |

## Authoritative commands

```bash
./scripts/verify.sh
./scripts/integration/verify-official-data.sh
./scripts/integration/verify-official-api.sh
./scripts/release/verify-release.sh
```

`./scripts/release/verify-release.sh` is the aggregate release entry point. It must only grow as the remaining gates arrive. The project is complete only when every release gate above is green on the integrated branch and a fresh clean-start rehearsal passes.
