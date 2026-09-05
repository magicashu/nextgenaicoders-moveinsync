# OWASP agent security source notes

## Sources reviewed

- [OWASP LLM06:2025 Excessive Agency](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/)
- [OWASP Prompt Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html)

## Decisions extracted

- Excessive agency comes from excessive functionality, permissions, or autonomy. Reduce all three.
- Offer only granular required tools; remove unused/open-ended extensions.
- Use least-privilege identities and user-context scopes. Analytical DB access is read-only and tenant constrained.
- Downstream systems validate every request; the LLM never authorizes an action.
- Approval requires execution-time validation of parameters, state, and permissions.
- Injection includes direct, remote, encoded, typoglycemic, HTML/Markdown, multi-turn, multimodal, RAG poisoning, prompt extraction, exfiltration, and tool manipulation.
- Filters and guard models are layers, not prevention. Combine separation, validation, least privilege, approval, monitoring, and rate limiting.

## Required tests

- Cross-tenant retrieval/metric query.
- Document instructing the model to ignore policy, call tools, or exfiltrate.
- Forged tool result resembling a supervisor instruction.
- Encoded/obfuscated variants.
- Unauthorized action, parameter escalation, stale approval, repeated action.
- Secret/PII in answer, trace, log, citation, or error.

Fail closed for access/actions while returning a useful non-sensitive explanation.
