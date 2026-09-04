# Mobility Decision Copilot — Integration Gate Board

This board is maintained by the Codex Foundation & Integration owner on `Java-branch-2`. A worker packet marked complete is only ready for integration; it is not a release pass. Update the evidence column with a commit and exact command result after every merge.

## Workstream intake

| Workstream | Branch | State | Required handoff evidence |
|---|---|---|---|
| Codex C0 metric/contracts | `Java-branch-2` | Green | `d4aa14d`; contract and serialization tests pass |
| Codex C1 build foundation | `Java-branch-2` | Green | `90c1683`; Java 21 configuration and one-command verification pass |
| Codex C2 application ports | `Java-branch-2` | Green | `90c1683`; control-plane consumer contracts pass |
| Codex C3 Spring composition | `Java-branch-2` | In progress | Application-start capability report is green; final adapter selection awaits worker handoffs |
| Codex official-data gate | `Java-branch-2` | Green | Current release-gate commit; seven checksums plus G1 M01 unit/API reconciliation pass |
| Claude WS1 governed analytics | `feat/governed-analytics` | Pending | Commit, tests, seven-file ingest and G1 evidence |
| Claude WS2 agent workflow | `feat/agent-workflow` | Pending | Commit, bounded trajectory tests and fallback proof |
| Claude WS3 governance/actions | `feat/governance-actions` | Pending | Commit, approval/resume/idempotency/audit tests |
| Claude WS4 product API | `feat/product-api` | Pending | Commit, provider/consumer and tenant tests |
| Claude WS5 React experience | `feat/react-experience` | Pending | Commit, UI tests/build and browser proof |
| Claude WS6 quality/telemetry | `feat/quality-telemetry` | Pending | Commit, evaluation/security/recovery/trace gates |

## Release gates

| Gate | State | Current evidence / exit condition |
|---|---|---|
| Organizer data integrity | Green | All seven files match `contracts/data/official-checksums.sha256` |
| Build and baseline suite | Green | 12 Java tests, React production build, 1 React test and fixture validation pass |
| Official G1 M01 | Green | 4,357 / 19,913 = 21.88%; baseline 12.28%; delta 9.60 pp |
| Official HTTP slice | Green | M01 reaches `AWAITING_APPROVAL` through the running Spring API |
| Complete G1 investigation | Red | Await WS1/WS2/WS4: M03/M04/M09/M11, concentration, vendor rebuttal and dual brief |
| G2 honest degradation | Red | Await implemented low-coverage/unsupported-metric behavior |
| G3 data-regime suppression | Red | Await data-quality classifier and no-action trajectory |
| Tenant isolation | Red | Await cross-business-unit API, tool and join regressions |
| Approval and one effect | Red | Await WS3 plus Codex C5 integration |
| Evidence coverage | Red | Await proof that every displayed numeric claim resolves to governed evidence |
| Root trace and telemetry fallback | Red | Await WS6 plus integrated Langfuse-degraded test |
| Browser judge flow | Red | Await WS4/WS5 plus Codex C6 integration |
| Clean-start release and backup | Red | Await C7 final Compose/start/reset and two repeated rehearsals |

## Authoritative commands

```bash
./scripts/verify.sh
./scripts/integration/verify-official-data.sh
./scripts/integration/verify-official-api.sh
./scripts/release/verify-release.sh
```

`./scripts/release/verify-release.sh` is the aggregate release entry point. It must only grow as the remaining gates arrive. The project is complete only when every release gate above is green on the integrated branch and a fresh clean-start rehearsal passes.
