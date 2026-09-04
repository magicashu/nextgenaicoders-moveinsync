# Integration notes for the Integration Owner (Codex)

All six branches compile, test and boot independently from `90c1683`. Because workers could not see each
other's code, each branch defines the ports it consumes in its own package and ships a scaffold or
in-memory implementation gated by a property. Integration is a `config/**` wiring job plus deletion of
the scaffolding. Nothing below changes a frozen D-041 signature.

## Property switches (set in `application.yml` / profiles)
| Property | Scaffold default | Integrated value | Effect |
|---|---|---|---|
| `mobility.workflow.analytics-gateway` | `scaffold` | `governed` | disables WS2 `ScaffoldAnalyticsGateway`; provide `AnalyticsGateway` adapter over WS1 |
| `mobility.workflow.control-plane` | `in-memory` | `postgres` (set by WS3 `application-postgres.yml`) | disables WS2 `InMemoryControlPlaneBeans` and WS3 in-memory beans; WS3 JDBC adapters serve the frozen ports |
| `mobility.workflow.language-model` | `none` | provider id | replace `LanguageModelPort.Unavailable` with a Spring AI `ChatClient` adapter |
| `mobility.api.gateway` | `scaffold` | `workflow` | disables WS4 `ScaffoldDecisionRunGateway`/`ScaffoldPortBeans`; provide `DecisionRunGateway` adapter |
| `mobility.api.actor-resolver` | `allowlist` | `governance` | replace WS4 `AllowlistActorResolver` with WS3 `RegistryIdentityResolver` |

## Adapters to write in `config/**`
1. `AnalyticsGateway` (WS2 port) over WS1: `detect` <- `AnomalyDetectionService.detect` (map `AnomalyCandidate` -> `DetectionSnapshot.IssueCandidate`, `RegimeChangeFinding` -> `DataQualityNote`); `runWorker` <- `WorkerContributionTools.run` (`WorkerEvidence` -> `WorkerEvidenceDto`, `ContributionRanking` -> `Ranking`, `Distribution` -> `Distribution`, `MetricWindow` <-> `WindowDto`); `metric` <- `MetricService.query`; `capabilities` <- `CapabilityMatrixService.matrix(...).statuses()`; `crossTenantPeers` <- `ContributionService.crossTenantDelayedTripRate`.
2. `DecisionRunGateway` (WS4 port) over WS2/WS3: `morningBrief/ask` <- `WorkflowCoordinator.run(...)` then `ResumableWorkflowEngine.find(runId)` -> `RunView` (fields are 1:1 with `WorkflowRun`, `BriefingOutput`, `EvidencePackage`, `VerificationResult`, `TransitionEvent`, `ModelUsage`); `decide` <- WS3 `ApprovalLifecycle.decide` for the audited transition, then `WorkflowCoordinator.resume(actor, decision)`; `findByApproval` <- `ApprovalRepository.findRecord(...).request().runId()`.
3. `ActorResolver` (WS4) over WS3 `RegistryIdentityResolver` with `TrustedIdentity(subject, businessUnit, roles, "gateway")`.
4. Telemetry: `TransitionListener` (WS2) -> WS6 `TraceRecorder.Trace.begin/end` keyed by run id; `ModelUsage` -> `Trace.recordModelCall`; `WorkerEvidence` calls -> `Trace.recordToolCall`; surface `TraceExporter.status()` in the trust panel.
5. Bind WS3 beans to the frozen ports: `ApprovalRepository`->`ApprovalStore`, `AuditLedger`->`AuditSink`, `DeterministicActionRevalidator`, `IdempotentActionExecutor`, `RoleBasedAccessAuthorizer`, checkpoint store.

## Scaffolding to delete at integration
`workflow/adapter/inmemory/InMemoryControlPlaneBeans.java`, `workflow/adapter/scaffold/ScaffoldAnalyticsGateway.java`, `reporting/adapter/ScaffoldDecisionRunGateway.java`, `reporting/adapter/ScaffoldPortBeans.java`, `api/security/AllowlistActorResolver.java`, WS2 `AllowedDimensions` (use WS1 `Dimension`). Keep WS3 in-memory adapters as the approved no-database fallback.

## Contract changes requested (shared)
- `decision-brief.schema.json`: status enum add `REPORT_ONLY`.
- OpenAPI 0.2.0 for the six product endpoints (shapes in `api/dto/ApiDtos.java`, mirrored in `frontend/src/core/contracts.ts`).
- Decision register: D-042 metric-fixture correction for M04/M05 late shares (contract denominator), LangGraph4j DROP verdict, REPORT_ONLY status, identity-header convention, evaluator-first evaluation and budgets, hash-chained append-only audit.
- Optional root dependencies: Spring AI in default scope (model roles), ArchUnit, opentelemetry-exporter-otlp.

## Effects of Integration Owner commits 19b14a9 / 8cd84c8 on these branches
- `contracts/data/official-checksums.sha256` matches WS1 `DatasetChecksums.OFFICIAL` digest for digest; WS1's `officialChecksumsAreUnchanged` and Codex's `verify-official-data.sh` agree.
- `MobilityCapabilitiesIntegrationTest` asserts `operatingMode == SCAFFOLD` with authorization and audit UNAVAILABLE. After merging WS2 (in-memory control-plane beans) or WS3 (real authorizer and ledger) those become AVAILABLE and the mode becomes READ_ONLY/FULL, so that Codex-owned assertion must follow the D-043 progression at each merge; no worker branch edits it.
- `OfficialDatasetReconciliationTest` uses `new DuckDbMetricService(MobilityDataProperties)`; WS1 kept that constructor, and the test passes on the rebased WS1 branch with the canonical dataset.
- WS2's `ScaffoldAnalyticsGateway` still works against the merged WS1 `DuckDbMetricService` because `MetricService.delayedTripRate` is unchanged; switch the property to `governed` once the adapter exists.

## Verification after each merge
`./mvnw -pl backend test` (WS1 official gates run in the canonical checkout), `npm ci && npm run build && npm test`, `MOBILITY_TEST_POSTGRES_URL=... ./mvnw -Ppostgres -pl backend test -Dtest=PostgresControlPlaneIntegrationTest`, `sh scripts/demo/verify-api.sh` with `MOBILITY_DATA_DIR` pointing at the official directory, then `sh scripts/demo/scorecard.sh` and the gated evaluation tests. Expected G1 through the real API: M01 21.88 vs 12.28, status AWAITING_APPROVAL, action CREATE_SITE_SHIFT_WATCHLIST scoped to Clearwater Campus with the morning shift band, all qualified vendors rose, approval -> revalidation -> one EXECUTED receipt -> audit chain.
