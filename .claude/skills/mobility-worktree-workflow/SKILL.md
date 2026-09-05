---
name: mobility-worktree-workflow
description: How Claude works on the MoveInSync Mobility Decision Copilot repo - baseline, worktrees, exclusive path ownership, frozen contracts, verification commands and the handoff format. Use at the start of any session that touches a docs/claude-workstreams packet.
---

# Mobility Copilot worktree workflow

Read `AGENTS.md`, `SESSION_CONTEXT.md`, `docs/parallel-delivery-plan.md`, `docs/hackathon-decision-register.md` (D-029 to D-041) and the packet in `docs/claude-workstreams/` first.

## Rules that bind every packet
- Integration branch is `Java-branch-2`; Codex owns it. Never commit, merge or push to it. Work in `../hackathon-wt-<packet>` on `feat/<packet>` created from the latest Integration Owner commit (`git worktree add ../hackathon-wt-x -b feat/x <commit>`).
- Commit only your packet's exclusive paths (`git add <owned paths>`; check `git status --short` for strays before committing).
- Frozen shared types and ports (D-041): `TenantContext`, `ActorContext`, `Permission`, `AccessAuthorizer`, `MetricQuery/MetricResult/MetricId/MetricUnit/MetricStatus`, `EvidenceBundle/EvidenceItem`, `DecisionBrief`, `ActionProposal/ActionType/ActionStatus/ExecutionReceipt/RevalidationResult/ActionExecutionCommand`, `ApprovalRequest/ApprovalDecision/ApprovalDecisionType/ApprovalStore`, `AuditEvent/AuditSink`, `WorkflowState/WorkflowOutcome/WorkflowEngine/WorkflowCheckpointStore`, `WorkflowProperties`, OpenAPI and JSON schemas. Extend alongside, never change signatures; request changes in the handoff.
- Anything your packet consumes from another packet does not exist on your branch: define a mirror port in your own package, ship an in-memory or scaffold implementation gated by a `@ConditionalOnProperty` so your branch boots alone, and list the mapping in the handoff for Codex's `config/**` adapter.
- Java 21 is not the default JDK on this machine: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` before `./mvnw`. Spring Boot 4.1.1 uses Jackson 3 (`tools.jackson.databind`) and `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- Spring cannot pick between two public constructors: annotate the injectable one with `@Autowired`.
- The official dataset lives only in the canonical checkout (`outputs/official dataset/...`, git-ignored). Tests gate on its presence and fall back to `data/fixtures/seven-file-sample`.

## Verification
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21); ./mvnw -q -pl backend test
npm ci && npm test && npm run build          # frontend
MOBILITY_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/mobility MOBILITY_TEST_POSTGRES_USER=mobility ./mvnw -Ppostgres -pl backend test -Dtest=PostgresControlPlaneIntegrationTest
sh scripts/demo/verify-api.sh                # boots, checks the demo endpoint, runs smoke
```

## Handoff format (return verbatim headings)
Owned paths / Branch and commit / Contract-version consumed / Feature demonstrated / Tests and exact result / Trace spans added / Known failure-fallback / Shared change requested / Decision-register update required / Integration steps. Handoffs written so far live in `.claude/handoffs/`.
