# Persona workspace delivery

Implemented locally on main; these changes have not been committed or pushed.

## Start the updated product

1. Stop the backend you previously launched with Ctrl+C in its terminal.
2. From the repository root, run `bash scripts/run-local-backend.sh`. The launcher loads the existing ignored provider configuration.
3. In another terminal, run `npm run dev` using the supported Node version in [the setup guide](try1-setup.md).
4. Reload the frontend. Select Transport Manager, Transport & Facilities Head, or Team / Line Manager and a business unit; click **Open my dashboard**.
5. Use **Switch persona** to return to selection. Analyse reuses the current capture; Refresh requests a replacement.

## Delivered behavior

- Persona selection precedes dashboard requests. Capture state is separated by business unit, date and role, and persists in tab session storage.
- Transport Managers receive operational findings and incident response options. Facilities Heads additionally receive leadership and cost summaries. Line Managers receive a read-only arrival, no-show and shift view.
- Ask Copilot reuses covered findings and presents bounded plain-English paragraphs. Evidence is collapsed and grouped; manager pages omit internal agent/node/model details.
- Incidents use real approval records, current status and expiry. Approve, narrow existing scope and approve, or dismiss with a reason. Confirmation shows the actual consequence. Response history shows human decision events.
- Corrected site/shift dimension matching and retained edited scope in subsequent approval previews. Backend edit validation rejects scope expansion.

## Verification performed

- Frontend TypeScript/Vite production build and Java 21 Maven package passed. Test suites remain skipped as requested. Vite reports its existing large-bundle advisory.
- Against the supplied dataset, dashboard values were M01 21.88%, M04 74.23%, M06 0.83%, and M09 1,020 for pinnacle-Slc, 1–7 June 2026; 50 shift rows were returned by the existing governed contribution service.
- All three personas returned their expected reports. Line Manager used the deterministic read-only path with zero model calls and no approval.
- Line Manager approval was denied with HTTP 403. Unknown edited shift was rejected with HTTP 409. Narrowing to an existing shift succeeded, was audited and remained visible on subsequent reads.
- Dismissal produced no action receipt; a repeated decision was rejected with HTTP 409.
- Eight concurrent reads reused the same capture. Refresh produced a new capture; subsequent reads reused it. Different dates and business units returned separate captures.
- Covered Transport Manager and Line Manager questions reused their report IDs.
- Browser inspection confirmed the persona gate and populated Line Manager arrival/shift dashboard. Repeated Analyse left its capture timestamp unchanged.
- Runtime verification used LANGUAGE_MODEL=none on a separate local backend. This work does not claim a fresh live Sarvam or Langfuse provider verification.

## Product boundaries

The supplied dataset has no manager-to-team assignment directory. This is a business-unit demo selector, not production authentication or employee-level access control. The CSV source is captured data, not a live ingestion feed. Incident responses use the existing simulated action adapter; recording a response does not fix the physical incident. The queue covers retained report proposals.

Caches are bounded and process-local; this removes repeated execution for unchanged selections but is not a million-user load certification. Multi-replica operation needs shared snapshots, durable jobs and production identity mapping. After a backend restart, Refresh replaces any browser-retained report whose backend run is no longer available.
