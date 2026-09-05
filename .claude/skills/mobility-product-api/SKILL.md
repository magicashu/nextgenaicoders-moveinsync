---
name: mobility-product-api
description: Change the REST endpoints, error envelope, dual-audience brief rendering or contextual question handling of the Mobility Decision Copilot (packet 04, feat/product-api). Use for controller, DTO, OpenAPI or conversation-scope work.
---

# Product API (WS4)

Owned: `backend/.../api/**`, `reporting/**`, `conversation/**`, matching tests.

## Contract
Endpoints: `GET /api/v1/briefs/morning`, `POST /api/v1/questions`, `POST /api/v1/workflows`, `GET /api/v1/workflows/{id}`, `GET|POST /api/v1/approvals/{id}[/decision]`, `GET /api/v1/audit/{id}`, plus scaffold `GET /api/v1/demo/brief`. Identity headers: `X-Actor-Id`, `X-Business-Unit`, `X-Roles`. DTOs in `api/dto/ApiDtos.java`; the React contracts (`frontend/src/core/contracts.ts`) mirror them field for field - change both together.

## Rules
- Controllers resolve identity, delegate to `DecisionRunGateway`/`ContextualQuestionService`/`AuditSink`, render with `BriefRenderer`. No SQL, metrics, workflow or approval logic in `api/**`.
- `BriefRenderer.assertNoDivergence` must stay empty: every number in the leadership narrative appears in the evidence bundle or operations findings (rounded-to-hundred forms and the bundle confidence are accepted).
- `ContextualQuestionService.classify` refuses SQL, external access, instruction override, execution requests and other tenants before any gateway call; intents map to the seven workers.
- Error envelope codes: VALIDATION_FAILED 400, ACCESS_DENIED 403, NOT_FOUND 404 (cross-tenant reads are indistinguishable from missing), UNSUPPORTED_CAPABILITY 422, INVALID_TRANSITION 409, DEPENDENCY_UNAVAILABLE 503, INTERNAL_ERROR 500.
- `ScaffoldDecisionRunGateway` and `ScaffoldPortBeans` boot the branch alone (`mobility.api.gateway=scaffold`, `mobility.api.actor-resolver=allowlist`); Codex swaps them for the workflow and governance adapters.

## Tests
`ProductApiTest` is a `@WebMvcTest` slice with `FakeGatewayConfig` (G1 numbers) - keep the scaffold controller out of the slice. `OpenApiProviderTest` checks the frozen 0.1.0 contract; OpenAPI 0.2.0 for the six endpoints is a shared change requested in the WS4 handoff.
