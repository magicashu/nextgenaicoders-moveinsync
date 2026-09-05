# UI integration verification — 2026-09-05

Integrated main commits `fab2e140937c8f7bb4bf35687d377b0945e60f08`,
`81d9da4b4f2e4f84530171517176006d51abf328` and
`3bf77e1d815adb1624d677856f267050cbb88f94` with the existing persona workspaces,
reusable report captures and incident responses. See decision D-051.

## Result

- Preserved the dashboard design and incoming chart/layout refinements while retaining real backend APIs, governed comparisons and actual audit/diagnostic records.
- Replaced incoming placeholder workflow APIs and synthetic metrics with the current integration contracts. Removed the obsolete investigation controller.
- Added a bottom-right chatbot icon opening text and voice controls across the workspace. There is no separate Ask navigation page.
- Added server-side question guardrails with plain unsupported-topic replies. Manager screens do not expose internal agent identities, counts or node badges.
- Added backend-only Sarvam read-aloud. Microphone input uses browser recognition with review before sending. No provider secret is in frontend code.

## Checks performed

| Check | Result |
| --- | --- |
| Frontend production build with Node 24 | Passed; existing large bundle advisory remains |
| Backend package with Java 21 and `-DskipTests` | Passed |
| Merge marker scan and `git diff --check` | Passed |
| 13 greeting, unrelated, random-text and injection requests | Friendly direct replies, no run ID, trust record or workers; no workflow started |
| Site/shift, delay-reason and cost questions | Evidence-backed answers reused the current report ID |
| General delay, transport-performance and trip-report questions | Reused the current report ID |
| Invalid speech voice and overlong text | Rejected with HTTP 400 |
| Live Sarvam speech request | HTTP 200, MP3 payload decoded to 43,467 bytes |
| Browser persona entry and dashboard | Rendered actual pinnacle-Slc June 8 capture and governed metrics |
| Browser floating chat | Opened, sent unsupported and supported questions, rendered replies and expanded structured evidence |
| Browser audio controls | Read-aloud entered playback state; Stop returned control; closing minimized the widget |
| Browser refresh and incident navigation | New capture populated a pending incident with response details and choices |

Local browser checks used Vite on port 15173 and an isolated backend on 18080.
The analytical workflow used `LANGUAGE_MODEL=none` for these checks; speech used
the configured live Sarvam key. These checks do not claim a fresh validation of
all LLM execution paths or Langfuse exports. Automated suites were skipped as
requested. Real microphone transcription and mobile-device testing were not run.

## Operational limits

The supplied dataset is a captured CSV dataset, not a live feed. Unchanged report
selections reuse captures; Refresh requests a new investigation. In-memory
approvals and run records reset on backend restart, so refresh the dashboard
after a restart before acting on an old incident. This is not a million-user
load certification or production authentication rollout.

See [setup](try1-setup.md) and [text/voice behavior](supervisor-voice-chat-copilot.md).
