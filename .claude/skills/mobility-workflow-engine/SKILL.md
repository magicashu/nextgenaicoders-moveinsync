---
name: mobility-workflow-engine
description: Change the deterministic 18-node workflow, the four bounded AI roles, the investigation subgraph, evidence verification or approval pause/resume in the Mobility Decision Copilot (packet 02, feat/agent-workflow). Use for orchestration, critic, briefing or LangGraph4j questions.
---

# Agent workflow (WS2)

Owned: `backend/.../workflow/**`, `evidence/**`, `resources/prompts/**`, matching tests.

## Shape
- `DeterministicWorkflowEngine` routes `WorkflowNode` 1-18; `InvestigationAgent` runs the four-node subgraph per task with a shared `WorkflowRun` (budget counters live in the frozen `WorkflowState`: `tryConsumeToolCall`, `tryConsumeCorrectionCycle`, `recordInvestigationDepth`).
- Ports the workflow consumes: `AnalyticsGateway` (mirrors WS1 `AnomalyDetectionResult`/`WorkerEvidence`/`CapabilityMatrix`), `LanguageModelPort` (default `Unavailable`), `TransitionListener`, plus the frozen control-plane ports. Composition selects real adapters with `mobility.workflow.analytics-gateway=governed`, `mobility.workflow.control-plane=postgres`, `mobility.workflow.language-model=<provider>`; otherwise the scaffold/in-memory beans in `workflow/adapter/**` keep the branch bootable.
- Roles are deterministic first; `ModelAssist` allows one attempt plus one parse retry and records `ModelUsage` (fallbackUsed) on every path. Model output is validated against allowlists (`WorkerType`, `AllowedDimensions`) and can only remove or flag, never add facts.
- `EvidenceMerger` builds `EvidencePackage` (content-hash `evidenceVersion`); `EvidenceVerifier` rejects uncited numbers, causal wording and single-vendor blame unless exactly one qualified vendor deteriorated; one correction cycle drops offending claims and re-verifies.
- `ActionPolicyGate` routes APPROVAL_REQUIRED / REPORT_ONLY / REJECTED; approval pauses at node 16 with a checkpoint; `resume(ApprovalDecision, RunContext)` re-enters node 17, recomputes detection (or replays nodes 3-11 after a restart) and executes only when the evidence version, approver tenant/permission and expiry all hold. Adapter failures keep APPROVED_NOT_EXECUTED.
- Additive `WorkflowStep` values: HEALTHY, REPORT_ONLY, EXECUTED, APPROVED_NOT_EXECUTED, REJECTED, EXPIRED. `DecisionBrief.status` may be REPORT_ONLY (schema change requested).

## LangGraph4j
Gate spike (1.8.26) passed routing, fan-out/fan-in, interrupt/resume, serialization and stream hooks; verdict DROP (deferred) recorded in `workflow/adapter/langgraph4j/README.md`. Do not add the dependency without an Integration Owner decision.

## Tests
`G1TrajectoryTest`, `FailurePathTest`, `ApprovalResumeTest` use `G1Fixtures` (fake gateway with reconciled numbers) and `EngineHarness` (in-memory ports). Keep every new path covered there before touching prompts.
