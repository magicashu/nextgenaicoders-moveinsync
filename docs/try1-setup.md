# Run the integrated application locally

The current implementation builds on Java-branch-2 and now uses LangGraph4j 1.8.25, replacing the
custom Java state-machine engine. The four agent roles use Sarvam for bounded
planning, investigation choices, critique and selection of verified briefing
claims. DuckDB computes the facts. The main graph has 18 business nodes; each
parallel investigator runs a four-node LangGraph4j analysis loop.

## 1. Stop older development servers

Use Ctrl+C in terminals running the old backend or frontend. The following setup
uses backend port 8080 and frontend port 5173.

### Quick start with both providers

On the configured local checkout, Sarvam and Langfuse credentials are saved in
`.env.backend.local`, which is ignored by Git and readable only by your OS user.
The launcher loads both providers automatically:

```bash
cd /Users/miniorange/Desktop/miniOrange-IAM/try/hackathon
bash scripts/run-local-backend.sh
```

The setup below describes entering both providers on a fresh checkout. Secret
values are not included in Git. The GitHub repository has `SARVAM_API_KEY`,
`LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` and `LANGFUSE_BASE_URL` configured as
repository secrets. GitHub Actions must explicitly pass these secrets to a job;
they are not automatically downloaded or exported on developers' machines.

Create a Sarvam API key at <https://dashboard.sarvam.ai/> and a Langfuse project
with public/secret API keys. In your backend terminal (zsh on this Mac):

```bash
cd /Users/miniorange/Desktop/miniOrange-IAM/try/hackathon
read -rs "SARVAM_API_KEY?Paste Sarvam API key: "
echo
read -rs "LANGFUSE_PUBLIC_KEY?Paste Langfuse public key: "
echo
read -rs "LANGFUSE_SECRET_KEY?Paste Langfuse secret key: "
echo
export SARVAM_API_KEY LANGFUSE_PUBLIC_KEY LANGFUSE_SECRET_KEY
export LANGFUSE_HOST=https://cloud.langfuse.com
bash scripts/run-local-backend.sh
```

Use your Langfuse region's host if it differs. The launcher requires all three
keys, loads the official dataset, builds without running tests and starts the
Sarvam-enabled backend. Keys entered this way remain in that terminal's process
environment; they are not persisted in Git or entered into shell history.
Start the frontend with step 3 below. The following backend steps are the manual
equivalent if you prefer to run each command yourself.

## 2. Start the backend — terminal 1

These commands use the JDK and official dataset already present on your Mac:

```bash
cd /Users/miniorange/Desktop/miniOrange-IAM/try/hackathon
export JAVA_HOME="/Users/miniorange/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export MOBILITY_DATA_DIR="$PWD/outputs/MoveInSync - Anonymised Trip-Log Dataset"
export ANALYTICS_GATEWAY=governed
export DECISION_RUN_GATEWAY=workflow
export ACTOR_RESOLVER=governance
export CONTROL_PLANE=in-memory
export LANGUAGE_MODEL=sarvam
export SARVAM_MODEL=sarvam-105b
export SARVAM_TIMEOUT=30s

# This checks the key without displaying it. Use the terminal where you exported it.
: "${SARVAM_API_KEY:?Set SARVAM_API_KEY in this terminal first}"

./mvnw -pl backend clean package -DskipTests
"$JAVA_HOME/bin/java" -jar backend/target/mobility-decision-copilot-backend-0.1.0-SNAPSHOT.jar
```

Keep this terminal open. Wait for Spring Boot to report that it has started.
The log should show `Sarvam enabled: model=sarvam-105b`. A missing key in explicit
Sarvam mode fails startup instead of silently starting without a model.
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
Choose your persona first, then open Dashboard, Incidents, Decision Brief, 3D Workflow or Audit Trail. Use the
bottom-right chat icon for questions, microphone input and read-aloud answers.
Evidence starts collapsed. Unchanged selections reuse the displayed capture;
Refresh explicitly requests a new investigation. See the [text and voice guide](supervisor-voice-chat-copilot.md).

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

## 6. Inspect the LLM calls and graph nodes

3D Workflow, Audit Trail and Decision Brief are available in the normal
navigation and share the selected report. Replay only animates recorded steps;
Reload events only reads the audit log. Expand a graph node or an audit event
for recorded decisions. To also expose **LLM & Trust** and **Scorecard**, set
`VITE_SHOW_DIAGNOSTICS=true` in frontend terminal 2 and restart Vite. Open **LLM & Trust** Expand **LLM execution** to
see each role's attempt, model, duration, input/output tokens, fallback reason and
structured proposal. Expand **Decision details** on each workflow node to see
the next node, selected issue, workers, validation results, critic verdict and
policy route. Tool details include filters and evidence references.

An LLM proposal is untrusted until subsequent nodes validate it. The UI shows
proposed worker/claim selections, not hidden chain-of-thought. Raw CSV rows,
prompts, full provider responses and credentials are not exported to tracing.
The model receives the role-specific aggregate/evidence JSON generated by the
agents, not the entire dataset. Healthy or unsupported-data routes can end
without calling all four agents. On an investigation route, the investigator
asks the model to choose the initial analysis and any permitted follow-up;
several parallel workers can therefore produce separate investigator calls.

The backend logs successful transport calls as `Sarvam completed: role=...`
with token counts. `Model fallback: ... reason=HTTP_AUTH` means a rejected key;
`HTTP_RATE_LIMIT`, `TIMEOUT` and malformed-output records explain other fallback
paths. `LANGUAGE_MODEL=none` deliberately disables calls; unset an old value or
override it with `sarvam` as shown above. `auto` uses Sarvam when a key exists.

To retrieve the compiled graph and one run's full inspection record:

```bash
curl http://localhost:8080/api/v1/workflows/graph

# Replace RUN_ID with the run shown in the Trust view.
curl -H 'X-Business-Unit: pinnacle-Slc' \
  http://localhost:8080/api/v1/workflows/RUN_ID/execution

curl -H 'X-Business-Unit: pinnacle-Slc' \
  http://localhost:8080/api/v1/audit/RUN_ID
```

The graph endpoint returns Mermaid generated from the compiled graph. Execution
records contain OTLP span data and exporter status, including failures/dropped
exports. The separate audit ledger records completed-node decisions, model usage,
approval decisions, revalidation and action receipts with the same run/trace ID.

## 7. Connect Langfuse

Create a Langfuse project and obtain its public and secret API keys from project
settings. In **backend terminal 1**, before starting Java, set these values
(replace placeholders; keep the secret key out of the frontend):

```bash
export LANGFUSE_BASE_URL="https://us.cloud.langfuse.com"
export LANGFUSE_PUBLIC_KEY="YOUR_PROJECT_PUBLIC_KEY"
export LANGFUSE_SECRET_KEY="YOUR_PROJECT_SECRET_KEY"
```

Use the host for your project's region or self-hosted instance. Restart the
backend. In **frontend terminal 2**, optionally set your project's URL:

```bash
export VITE_LANGFUSE_URL="https://us.cloud.langfuse.com/project/YOUR_PROJECT_ID"
npm run dev
```

Run another brief, open the Langfuse link in **Trust**, or search Langfuse by the
trace ID. Expand the node tree and `llm.*` generation spans for model name, usage,
duration, structured proposals and fallback status. Parallel workers appear
under the investigation node. Trace exports occur at approval pause and at a
terminal outcome. Approval resume adds the execution/audit nodes to the same trace.

The backend uses OTLP over HTTP/JSON with Langfuse generation attributes and
Basic authentication. It does not use the Python Langfuse SDK or an automatic
Java-agent integration. Transport failures cannot block business execution.
Token usage comes from Sarvam; no token prices or cost figures are invented.
Configure a custom model price in Langfuse if automatic cost mapping is absent.

Reference: [Langfuse OpenTelemetry integration](https://langfuse.com/integrations/native/opentelemetry).

## 8. Optional local OpenTelemetry visualization

With Docker running, start Jaeger in a third terminal:

```bash
docker run --rm --name mobility-jaeger \
  -p 127.0.0.1:16686:16686 -p 127.0.0.1:4318:4318 \
  cr.jaegertracing.io/jaegertracing/jaeger:2.20.0
```

Set this in the backend terminal and restart Java:

```bash
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://localhost:4318/v1/traces"
```

Open <http://localhost:16686>, run a new investigation, and select service
`mobility-decision-copilot`. This setting supports an OTLP HTTP/JSON collector.
Langfuse and the collector can both be enabled; each receives the same trace IDs.
This local Jaeger configuration stores traces in memory.

References: [Jaeger container](https://www.jaegertracing.io/download/),
[OTLP HTTP protocol](https://opentelemetry.io/docs/specs/otlp/).

## Current verification and persistence limits

- Backend compilation/packaging and frontend production build are checked.
- Test suites were deliberately not run, per the current user instruction.
- The official-dataset runtime reached the native LangGraph4j approval pause.
- Langfuse US credentials were supplied and configured in the ignored local
  backend file. The real backend exported a 56-span official-dataset trace with
  one successful export, zero failures and zero dropped traces.
  A read-back from Langfuse's Observations API confirmed all 56 observations.
  [Open the verified trace](https://us.cloud.langfuse.com/project/cmto8jina07psad0dw6adyw2d/traces/3f126cad07554bb7925421d954ad03b7).
  The frontend project link is configured in its ignored `.env.local` file.
- Sarvam's live API key is not available in the agent process. That trace used
  deterministic fallback; live Sarvam completion still needs verification from
  the backend terminal containing the key.
- LangGraph4j graph checkpoints are currently in memory. Business approval and
  workflow checkpoints use the existing selectable control-plane repositories.
  With PostgreSQL configured, approval resume after restart rebuilds/revalidates
  evidence; it is not arbitrary-node recovery of a full durable LangGraph state.
- Local trace/run retention is in memory, not a million-user production store.
  PostgreSQL behavior and production scaling have not been revalidated here.

Sarvam request format follows the [current chat API](https://docs.sarvam.ai/api-reference/chat/chat-completions).

Unfinished work from main is preserved in the named Git stash
`try1: preserve unfinished main UI/API work before adopting Java-branch-2`.
The branch integration is local; it has not been pushed.

## Dashboard frontend from main

The frontend design from commit `1ea1fb8de5e46ab84196ebada960a5b3a1e41467` is integrated on `try1`.
Restart the backend to expose the new governed dashboard endpoint, and install the chart/3D dependencies:

```bash
# Terminal 1, repository root; SARVAM_API_KEY must have a value in this terminal
bash scripts/run-local-backend.sh

# Terminal 2, repository root; use the supported Node version described above
npm ci --registry=https://registry.npmjs.org --no-audit --no-fund
npm run dev
```

Open http://localhost:5173. First choose a persona and business unit, then **Open my dashboard**. No dashboard request starts before this choice. The initial date window is `2026-06-01 → 2026-06-07`.
The picker retains the incoming design; the current backend requires seven consecutive days and compares against the preceding four weeks.

- **Transport Manager:** operational findings, arrival reliability, vendor/site comparisons and incident responses.
- **Transport & Facilities Head:** performance and cost overview, leadership report and incident responses.
- **Team / Line Manager:** read-only arrival, no-show and shift information. No investigation or approval permissions. Business-unit data is not an authenticated manager-to-team mapping.
- **Bottom-right chatbot icon:** simple-English explanations; covered questions reuse current findings. Supporting evidence is grouped and collapsed initially.
- **Reports:** readable narratives and supporting figures.
- **Incidents:** actual proposals from retained captures, with current approval state, affected scope, review expiry and a confirmation step. Approve, narrow existing multi-site/shift scope where available, or dismiss with a reason. Actions remain simulated; recording a response does not resolve the underlying real-world issue.

**Analyse** reuses the same role/business-unit/date capture. **Refresh** explicitly captures a replacement and keeps the previous data visible while it loads. Changing dates or business unit loads the matching capture or creates one if absent. **Switch persona** returns to the selection screen. Reload returns to selection but retains up to eight tab-local captures.

Developer diagnostics are hidden by default. To inspect recorded node decisions, Sarvam calls and Langfuse links, set `VITE_SHOW_DIAGNOSTICS=true` in `frontend/.env.local` and restart Vite; this enables **3D Workflow**, **LLM & Trust**, **Audit Trail** and **Scorecard**. These views replay records; they are not a live event stream. Provider credentials stay on the backend.

The CSV source is not a live ingestion feed. Backend caches are bounded and process-local; production with multiple replicas needs a shared capture/job store and durable coordination. The incident queue currently covers retained reports rather than complete server-side history.
The browser calls the local backend; Sarvam and Langfuse secret keys remain backend-only.
For a non-default backend port, launch Vite with `VITE_API_TARGET=http://127.0.0.1:18080 npm run dev`.

If the backend was restarted and a retained report's response details are unavailable, use **Refresh** to capture a new report. Session storage preserves the display, but the default local backend does not preserve all run objects across restarts.
