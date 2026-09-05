# Local setup and running the agents

The supported local path is Java 21, the Maven wrapper and the supplied CSVs. DuckDB is embedded. Docker Compose runs four separate role containers over the official data and Sarvam.

## 1. Prerequisites and checkout

Install a JDK 21 and Git LFS. Run all commands below from the repository root. On macOS, select an installed JDK with:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
./mvnw -version
git lfs install
git lfs pull
git lfs ls-files
```

On Linux/Windows, set `JAVA_HOME` to your JDK 21 installation; Windows can use `mvnw.cmd`. The wrapper downloads Maven/dependencies on first use, so network access is needed then. A fresh checkout can be obtained with `git clone https://github.com/magicashu/nextgenaicoders-moveinsync.git`.

## 2. Point to the actual dataset

```sh
export MOBILITY_DATA_DIR="$PWD/outputs/MoveInSync - Anonymised Trip-Log Dataset"
export MOBILITY_AI_PROVIDER=none
export MOBILITY_ANALYTICS_DATABASE="$PWD/data/analytics.duckdb"
```

Keep the supplied CSV names and bytes unchanged. If files contain short Git LFS pointer text instead of CSV contents, finish `git lfs pull` first. The analytical snapshot is created lazily on the first metric request. It is pinned to a source hash and rebuilt if the source/storage version changes. Allow writable space for the derived database and temporary spill files; use a separate database path per writer process.

## 3. Run the complete dataset-backed validation

```sh
./mvnw -pl backend test "-DofficialDataset=$MOBILITY_DATA_DIR"
```

This runs all 20 business scenarios, source reconciliation, concurrency checks and control tests. The recorded implementation run passed 50 tests with no failures/errors/skips. Without the `officialDataset` property, the actual-data test class is skipped. The integration test uses a derived database under `/private/tmp`; the currently supplied test path assumes macOS or a Unix environment with that writable directory.

For only the dataset scenarios:

```sh
./mvnw -pl backend test -Dtest=OfficialDatasetScenariosTest "-DofficialDataset=$MOBILITY_DATA_DIR"
```

The scenarios exercise the four agents without a live language model. See [the implementation review](../evals/backend-implementation-review.md) for exact coverage and limits.

## 4. Start the backend

```sh
./mvnw -pl backend spring-boot:run
```

For local prompt testing, enable local API explicitly:

```sh
export MOBILITY_AI_PROVIDER=sarvam
export SARVAM_API_KEY='your-key'
export SARVAM_MODEL='sarvam-105b'
export SARVAM_ENDPOINT='https://api.sarvam.ai/v1/chat/completions'
./mvnw -pl backend spring-boot:run
```

This endpoint uses a fixed local Transport Manager identity. It is not production authentication.

## 5. Test with an actual prompt

Supervisor prompt test:

```sh
curl --fail --silent --show-error \
	-H 'Content-Type: application/json' \
	-d '{"businessUnit":"pinnacle-Slc","asOf":"2026-06-08","prompt":"Where is this delay anomaly concentrated by site and shift?"}' \
	http://localhost:8080/api/v1/agent/supervisor | jq
```

Pass returned `plan` to `/api/v1/agent/investigator`, returned `investigation` to `/api/v1/agent/evidence-critic`, then returned `verification` to `/api/v1/agent/briefing-action`. Each endpoint executes one role only. This is local testing only; production authentication is not implemented.

Question must mention one governed metric. Examples:

```text
Where is this delay anomaly concentrated by site and shift?
Did every high-volume vendor deteriorate on pickup?
What evidence explains the low driver rating rate?
```

The router rejects ambiguous or unsupported questions. Sarvam output only selects among allowlisted investigations; deterministic metrics, scope and evidence verification remain authoritative.

In another terminal:

```sh
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/api/v1/capabilities
```

Expect health `UP` and capabilities listing all 18 implemented contracts. `governedRuntimeReady: false` is intentional: authenticated serving and durable action execution are not finished. Health does not prove that the CSVs were ingested, because analytics initializes lazily. Run the dataset tests to verify ingestion and the complete reporting path. Set `SERVER_PORT` if 8080 is occupied; use Ctrl-C to stop.

To invoke agents from trusted backend code, inject `AgentWorkflowService`. Use `investigate(RunContext, MetricRequest)` for a governed metric or `execute(RunContext, InvestigationPlan)` for a reviewed multi-domain plan. Build the context from authorized actor/tenant information and pin the data version from `OfficialAnalyticsStore.dataVersion()`, metric registry version, window and budget. The integration test supplies working examples. Do not construct tenant authorization from an untrusted request header.

## 6. Optional UI

The current UI provides a dashboard, 3D workflow, briefs, audit and scorecard backed by `frontend/src/core/mockData.ts`; it is not connected to the completed backend reporting path. To inspect it, use Node 22.13+ within the 22.x line or Node 24+ as required by the lockfile, then run:

```sh
npm ci
npm run dev
```

Vite serves port 5173 and proxies `/api` to port 8080. The displayed mock values are not evidence of a live agent run.

Sarvam is the configured model provider for prompt testing. Do not set `MOBILITY_AI_PROVIDER=ollama`. Deterministic validation remains active and model output cannot override evidence or approve actions.

## Operational settings and troubleshooting

Defaults under `mobility.analytics` are 512 MB DuckDB memory, two SQL threads, four connections, 30-second SQL timeout, 256 cache entries and 100 returned groups. For example, set `MOBILITY_ANALYTICS_MEMORY_LIMIT=1GB` or `MOBILITY_ANALYTICS_CONNECTIONS=4`. These are resource bounds, not production throughput guarantees.

- Java compilation/release errors: check `./mvnw -version` actually uses JDK 21.
- Missing dataset or parse errors: check the absolute data path and LFS download; do not substitute synthetic fixtures for official business tests.
- Database lock errors: stop the other writer or choose another `MOBILITY_ANALYTICS_DATABASE` path.
- Unavailable metrics: inspect evidence caveats and eligible populations. Q2 cost/km restrictions and empty severe-alert populations are expected results.
- Capacity/timeouts: inspect the workload and configured limits before increasing parallelism; exact quantiles can cost more than rollup counts.

## 7. Run separated Docker agents

Export Sarvam credentials, then start four role containers:

```sh
export SARVAM_API_KEY='your-key'
docker compose up --build
```

Endpoints: Supervisor `http://localhost:8081`, Investigator `http://localhost:8082`, Evidence Critic `http://localhost:8083`, Briefing/Action `http://localhost:8084`, frontend `http://localhost:3000`. Each container mounts official data read-only and owns separate DuckDB runtime storage. Frontend proxy routes each role path to its matching container.
