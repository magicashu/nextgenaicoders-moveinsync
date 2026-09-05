# Run try1 locally

`try1` adopts the implementation from `Java-branch-2` at `ee3e755`.
It runs four agent roles through an 18-node Java workflow with DuckDB analytics,
questions, anomaly investigation, approvals and audit views.

## 1. Stop older development servers

Use Ctrl+C in terminals running the old backend or frontend. The following setup
uses backend port 8080 and frontend port 5173.

## 2. Start the backend — terminal 1

These commands use the JDK and official dataset already present on your Mac:

```bash
cd /Users/miniorange/Desktop/miniOrange-IAM/try/hackathon
git switch try1
export JAVA_HOME="/Users/miniorange/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export MOBILITY_DATA_DIR="$PWD/outputs/MoveInSync - Anonymised Trip-Log Dataset"
export ANALYTICS_GATEWAY=governed
export DECISION_RUN_GATEWAY=workflow
export ACTOR_RESOLVER=governance
export CONTROL_PLANE=in-memory
export LANGUAGE_MODEL=none
./mvnw -pl backend spring-boot:run
```

Keep this terminal open. Wait for Spring Boot to report that it has started.
The dataset is loaded into DuckDB on startup; no manual SQL import is needed.
Use the path above: older Java-branch-2 documentation includes an extra
`official dataset` directory that is absent in this workspace.

## 3. Start the frontend — terminal 2

```bash
cd /Users/miniorange/Desktop/miniOrange-IAM/try/hackathon
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"
node --version
npm ci --registry=https://registry.npmjs.org --no-audit --no-fund --fetch-retries=0 --fetch-timeout=30000
export VITE_USE_MOCKS=false
npm run dev
```

The Node command should show version 24 on this Mac. `npm ci` installs Vite and
the other locked dependencies; run it after switching branches or changing the
lockfile. Subsequent starts only need the PATH export and `npm run dev`.

## 4. Open the application

Open <http://localhost:5173>. Use business unit `pinnacle-Slc` and date
`2026-06-08`, which matches the supplied dataset's verified scenario.
Explore the morning brief, investigation, approval and audit views. Use the
Ask interface or a suggested question to ask about the current brief.

This local configuration requires neither an operator token nor PostgreSQL.
Approvals, checkpoints and audit state are held in memory and reset when the
backend restarts. Approved actions use mock effects.

## 5. Check the backend if the page cannot load

In another terminal:

```bash
curl http://localhost:8080/actuator/health
curl -H 'X-Business-Unit: pinnacle-Slc' \
  'http://localhost:8080/api/v1/briefs/morning?asOf=2026-06-08'
```

If port 8080 is already occupied, stop the older server before retrying. The
frontend proxy is configured to use that port.

## LLM status

This adopted Java-branch-2 implementation defaults to deterministic agent
behavior. It has a language-model interface but no concrete Sarvam adapter.
Exporting `SARVAM_API_KEY` alone does not enable LLM calls on this branch.
The prior Sarvam implementation remains in main's Git history and needs to be
adapted to this branch's different model interface. This workflow uses the Java
state machine, not LangGraph.

## Verification performed

- Clean backend build and tests: 127 passed, including official-dataset metric
  reconciliation and the investigation/approval/resume flow.
- Frontend production build passed; all 7 frontend tests passed.
- PostgreSQL integration was not exercised; the local setup uses memory.

To repeat backend checks, use terminal 1's Java settings, stop the server, then:

```bash
export MOBILITY_OFFICIAL_DATA_DIR="$MOBILITY_DATA_DIR"
./mvnw -pl backend clean test
```

To repeat frontend checks in terminal 2:

```bash
npm run build
npm test
```

Unfinished work from main is preserved in the named Git stash
`try1: preserve unfinished main UI/API work before adopting Java-branch-2`.
The branch integration is local; it has not been pushed.
