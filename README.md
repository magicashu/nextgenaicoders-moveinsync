# Mobility Decision Copilot

Java 21/Spring Boot and React/TypeScript starter for the MoveInSync agentic intelligence and reporting challenge.

The checked-in product is a deterministic, evidence-governed vertical slice:

`request -> tenant/RBAC -> DuckDB metrics -> anomaly detection -> four agent roles -> evidence critic -> dual brief -> approval -> revalidation -> idempotent mock effect -> audit/trace`

It intentionally does not include RAG, a vector database, unrestricted text-to-SQL, or independent agent microservices.

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 22+
- Docker (optional)

## Run with the tiny fixture

```bash
./mvnw -pl backend spring-boot:run
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite development server proxies `/api` to Spring Boot on port 8080.

The default dataset is `data/fixtures`. To use the immutable organizer files:

```bash
export MOBILITY_DATA_DIR="$PWD/outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset"
./mvnw -pl backend spring-boot:run
```

The product endpoint is:

```bash
curl -H 'X-Business-Unit: pinnacle-Slc' \
  'http://localhost:8080/api/v1/briefs/morning?asOf=2026-06-08'
```

The default runtime uses governed DuckDB analytics, the 18-node LangGraph4j workflow,
registry-backed tenant/RBAC checks, the in-memory local control plane and an in-memory trace
exporter. No external secret is required for the local demo.

## Optional profiles

- `-Pai-openai`: adds the Spring AI OpenAI model starter. Set `OPENAI_API_KEY` before activating it.
- `-Ppostgres`: adds PostgreSQL/Flyway dependencies. Run with Spring profile `postgres`.
- LangGraph4j 1.8.25 now owns graph execution and approval interruption (D-046), replacing the custom state-machine engine.
- Set `LANGUAGE_MODEL=sarvam` and `SARVAM_API_KEY` in the backend terminal to enable Sarvam. Automatic mode also detects a nonempty key. See [current setup and tracing guide](docs/try1-setup.md).
- Langfuse export is optional. Set `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY`; without them tracing remains available locally and does not block the product.

## Release verification

```bash
./scripts/release/verify-release.sh
```

This checks organizer-file integrity, all Java and React tests, official metric reconciliation,
the running product API, approval/resume, tenant isolation, adversarial requests, G1/G2/G3 and the
generated scorecard.

## Important paths

- `docs/project-structure.md` — folder ownership and boundary rules
- `backend/src/main/resources/sql/metrics/` — governed metric SQL
- `backend/src/main/resources/prompts/v1/` — versioned agent prompts
- `contracts/` — public API and JSON schemas
- `evals/` — golden and adversarial cases
- `data/fixtures/` — tiny synthetic inputs only

Never modify the files under `outputs/official dataset/`.
