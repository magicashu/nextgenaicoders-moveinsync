# Floating text and voice Copilot

Integrated on 2026-09-05 with D-051. This document supersedes the incoming voice feature proposal.

## User experience

Choose a persona and business unit before opening the dashboard. The bottom-right chat icon is available on Dashboard, Incidents, Decision Brief, 3D Workflow and Audit Trail. It opens a shared panel with text input, a microphone and explicit read-aloud controls. The header shows the selected business unit and report date.

Answers use plain English. Supporting evidence starts collapsed and groups findings by business topic. Chat, dashboard and incident text do not display internal agent identities, counts, node names or execution badges. The explicitly requested 3D Workflow and Audit Trail screens expose recorded execution details. LLM & Trust and Scorecard remain separately opt-in.

Closing the panel preserves the current conversation. Changing persona, business unit or report capture clears it and stops pending audio. Messages stay in component memory, bounded to 40; they are not persisted across reloads.

## Actual data flow

1. Text or a reviewed voice transcript is sent to `POST /api/v1/questions` with the selected identity, date and current report ID.
2. Deterministic server guardrails handle greetings, unrelated input, attempts to expose internals, injection patterns and cross-business-unit requests before investigation.
3. Supported questions reuse the selected report when it contains the requested findings. Otherwise, the existing scoped investigation API supplies evidence.
4. The panel renders plain text and structured, collapsible evidence. It does not fabricate metrics, execution progress or audit records.
5. The latest visible answer is prepared silently in the selected voice. Read aloud reuses this audio; other answers send answer text to `POST /api/v1/speech`. The backend authorizes the tenant, validates text and voice, and calls Sarvam. The browser receives audio, never an API key.

## Voice input and output

**Input:** browser SpeechRecognition, when supported, with microphone permission. Recognition currently uses English (India), stops after at most 30 seconds and fills a 500-character editable question. The user reviews it and clicks Send. This is not Sarvam STT and is not an automatic hands-free conversation. Permission denial or browser incompatibility has a visible text fallback.

**Output:** server-side Sarvam `bulbul:v3`, English (India), MP3, with three voice choices. The speech endpoint accepts at most 2,500 characters, allows only configured speakers, limits concurrent synthesis to two and caches up to 16 responses scoped by actor, tenant and role. Provider requests and response bodies are bounded. The browser prepares only the latest answer, deduplicates in-flight requests, and keeps up to eight identity/voice/text-scoped audio responses in memory. Audio plays only after a click. If synthesis is still pending four seconds after that click, browser speech starts with a notice when available; the provider response may finish for later replay but never interrupts current speech. Requests time out after 30 seconds. Provider failures also use browser speech where available. Preparation can incur a provider request even if the user does not listen. Playback can be stopped; closing or changing context cancels pending playback.

The provider contract follows [Sarvam's official speech API documentation](https://docs.sarvam.ai/api-reference/text-to-speech/convert).

## Setup

Follow [the local setup guide](try1-setup.md). Set `SARVAM_API_KEY` only in the backend terminal or ignored backend environment file; restart the backend after changing it. The frontend needs only its backend proxy target. Do not add provider secrets to any `VITE_*` variable.

Use localhost or HTTPS for microphone access in a compatible browser, then grant permission when you choose to dictate. Text chat works without microphone access. Enable Sarvam as the language model separately using the setup guide; speech output uses the backend key independently.

## Security and limits

Guardrails are deterministic scope checks and allowlisted question intents, not a general-purpose chatbot or a claim that every adversarial phrase can be recognized. Question text cannot choose credentials, tenant authorization or arbitrary tools. Answers come from governed evidence; incident actions continue to require their separate confirmation and authorization.

Local demo identity headers do not replace production authentication. Internal execution records remain available to developer diagnostic APIs; hiding diagnostic navigation is presentation, not access control. Production deployment requires authenticated identity and restricted audit access.

See [integration verification](ui-sync-verification.md) for the checks actually performed. Automated suites and live microphone transcription were not run during this integration.
