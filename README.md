# Mobility Decision Copilot

> Branch scope: Java-branch carries the updated architecture documents while retaining its existing scaffold code. The integrated implementation and new code stay on Java-branch-2. See [architecture branch scope](docs/architecture-branch-scope.md) and the [current HLD](docs/high-level-design.md). Feature/test claims in those documents refer to Java-branch-2; the setup below describes this branch's unchanged scaffold.

Java 21/Spring Boot and React/TypeScript starter for the MoveInSync agentic intelligence and reporting challenge.

The checked-in sample is a deterministic vertical slice:

`request -> tenant context -> M01 in DuckDB -> anomaly rule -> evidence check -> brief -> draft action`

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

The demo endpoint is:

```bash
curl -H 'X-Business-Unit: pinnacle-Slc' \
  'http://localhost:8080/api/v1/demo/brief?asOf=2026-06-08'
```

## Optional profiles

- `-Pai-openai`: adds the Spring AI OpenAI model starter. Set `OPENAI_API_KEY` before activating it.
- `-Ppostgres`: adds PostgreSQL/Flyway dependencies. Run with Spring profile `postgres`.
- LangGraph4j is not included until the D-028 spike passes; the sample uses the project-owned deterministic workflow engine.

## Important paths

- `docs/project-structure.md` — folder ownership and boundary rules
- `backend/src/main/resources/sql/metrics/` — governed metric SQL
- `backend/src/main/resources/prompts/v1/` — versioned agent prompts
- `contracts/` — public API and JSON schemas
- `evals/` — golden and adversarial cases
- `data/fixtures/` — tiny synthetic inputs only

Never modify the files under `outputs/official dataset/`.
