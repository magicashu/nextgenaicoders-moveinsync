---
name: mobility-governance-actions
description: Change tenant access, approval lifecycle, checkpoint persistence, idempotent action execution or the append-only audit ledger of the Mobility Decision Copilot (packet 03, feat/governance-actions). Use for PostgreSQL/Flyway, revalidation, idempotency or audit questions.
---

# Governance and actions (WS3)

Owned: `backend/.../access/**`, `approval/**`, `action/**`, `audit/**`, `resources/db/migration/**`, `application-postgres.yml`, matching tests.

## Invariants
- Identity comes from `TrustedIdentity` (edge headers/claims) resolved by `RegistryIdentityResolver` against the five official tenants and the `Role` allowlist; `RoleBasedAccessAuthorizer` fails closed on any tenant mismatch. Never read tenant or role from prose.
- Approval state machine (`ApprovalLifecycle`): PENDING -> APPROVED | REJECTED | EDITED | EXPIRED; edits keep action/run ids, evidence version and tenant, cannot extend expiry or upgrade to a vendor escalation; one pending approval per action (unique partial index).
- `DeterministicActionRevalidator` re-checks authorization, approval status, evidence version, expiry, tenant and prior execution. `IdempotentActionExecutor` claims the key first (`INSERT ... ON CONFLICT DO NOTHING`), waits briefly for an in-flight twin, and returns the stored receipt to duplicates; adapter failure -> APPROVED_NOT_EXECUTED, retryable only because mock adapters produce no effect when they throw.
- Audit is append-only with a per-run SHA-256 hash chain; Flyway V2 installs a trigger that rejects UPDATE/DELETE/TRUNCATE on `audit_event`.
- JDBC adapters use only `javax.sql`/`java.sql` so they compile without the postgres profile; `ControlPlaneBeans` selects them when `mobility.workflow.control-plane=postgres` (set by the postgres profile), otherwise in-memory adapters.

## Local PostgreSQL for tests
A scratch cluster on port 55432 (`initdb ... --auth=trust`, user `mobility`, db `mobility`) is the fastest path; run
`MOBILITY_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/mobility MOBILITY_TEST_POSTGRES_USER=mobility ./mvnw -Ppostgres -pl backend test -Dtest=PostgresControlPlaneIntegrationTest`.
The test applies V1 and V2 over plain JDBC and proves optimistic checkpoints, race-safe decisions, cross-process idempotency and the append-only trigger.
