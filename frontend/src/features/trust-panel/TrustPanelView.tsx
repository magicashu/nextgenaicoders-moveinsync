import type { TrustPanel } from '../../core/contracts'

type Props = { trust: TrustPanel; traceUrl: string | null }

/** Versions, capability gaps, latency, model use and the node-by-node transition trace (no chain-of-thought). */
export function TrustPanelView({ trust, traceUrl }: Props) {
  const mainNodes = trust.transitions.filter((t) => !t.subNode)
  const modelSpans = trust.transitions.filter((t) => t.subNode?.startsWith('llm.') || t.subNode?.startsWith('fallback.'))
  const toolSpans = trust.transitions.filter((t) => t.subNode && !t.subNode.startsWith('llm.') && !t.subNode.startsWith('fallback.'))
  return (
    <>
      <section className="panel">
        <p className="eyebrow">Trust record</p>
        <h3>Everything shown is versioned, traceable and reproducible</h3>
        <dl className="kv grid">
          <div><dt>Run</dt><dd><code>{trust.runId}</code></dd></div>
          <div><dt>Trace</dt><dd><code>{trust.traceId}</code>{traceUrl && <> · <a href={`${traceUrl.replace(/\/$/, '')}/traces/${encodeURIComponent(trust.traceId)}`} target="_blank" rel="noreferrer">Langfuse</a></>}</dd></div>
          <div><dt>Final step</dt><dd>{trust.finalStep}</dd></div>
          <div><dt>Data version</dt><dd>{trust.dataVersion}</dd></div>
          <div><dt>Metric contract</dt><dd>{trust.contractVersion}</dd></div>
          <div><dt>Workflow</dt><dd>{trust.workflowVersion}</dd></div>
          <div><dt>Prompts</dt><dd>{trust.promptVersion}</dd></div>
          <div><dt>Model</dt><dd>{trust.modelId === 'none' ? 'none (deterministic roles)' : trust.modelId}</dd></div>
          <div><dt>Anomaly rules</dt><dd>{trust.ruleVersion}</dd></div>
          <div><dt>Targets</dt><dd>{trust.targetVersion} (configured per tenant)</dd></div>
          <div><dt>Latency</dt><dd>{trust.latencyMs.toLocaleString('en-IN')} ms end to end</dd></div>
          <div><dt>Model calls</dt><dd>{trust.modelCalls} ({trust.fallbackCalls} deterministic fallbacks) · {trust.inputTokens + trust.outputTokens} tokens</dd></div>
          <div><dt>Tool calls</dt><dd>{trust.toolCalls} governed analytical calls</dd></div>
          <div><dt>Confidence</dt><dd>{trust.confidence === null ? '—' : `${Math.round(trust.confidence * 100)}%`} {trust.confidenceComponents.length > 0 && <small>({trust.confidenceComponents.join(', ')})</small>}</dd></div>
        </dl>
      </section>
      <section className="content-grid">
        <article className="panel">
          <p className="eyebrow">Capability gaps</p>
          {trust.capabilityGaps.length === 0 ? <p className="muted">All analyses supported for this tenant and data version.</p> : (
            <ul className="caveats">{trust.capabilityGaps.map((g) => <li key={g}>{g}</li>)}</ul>
          )}
          <p className="eyebrow">Data-quality notes</p>
          {trust.dataQualityNotes.length === 0 ? <p className="muted">None for this run.</p> : (
            <ul className="caveats">{trust.dataQualityNotes.map((n) => <li key={n}>{n}</li>)}</ul>
          )}
        </article>
        <article className="panel">
          <p className="eyebrow">Investigation branches</p>
          <ul className="branch-list">
            {Object.entries(trust.branchStatus).map(([worker, status]) => (
              <li key={worker}><span>{worker}</span><span className={`pill pill--${status.toLowerCase()}`}>{status}</span></li>
            ))}
          </ul>
        </article>
      </section>
      <section className="panel">
        <p className="eyebrow">LLM execution</p>
        <h3>{trust.modelCalls} model attempts · {trust.inputTokens + trust.outputTokens} tokens</h3>
        {modelSpans.length === 0 ? <p>No LLM calls were needed on this route.</p> : modelSpans.map((t, i) => (
          <details key={`${t.subNode}-${i}`} className="model-execution">
            <summary>{t.attributes?.['model.role'] ?? t.subNode} · {t.outcome} · {t.durationMs} ms</summary>
            <dl className="kv">
              <div><dt>Model</dt><dd>{t.attributes?.['gen_ai.request.model'] ?? trust.modelId}</dd></div>
              <div><dt>Input / output tokens</dt><dd>{t.attributes?.['gen_ai.usage.input_tokens'] ?? '0'} / {t.attributes?.['gen_ai.usage.output_tokens'] ?? '0'}</dd></div>
              <div><dt>Result</dt><dd>{t.attributes?.['model.note']}</dd></div>
              <div><dt>Structured proposal</dt><dd><pre>{t.attributes?.['langfuse.observation.output']}</pre></dd></div>
            </dl>
            <p className="muted">The following graph nodes validate this proposal before using it.</p>
          </details>
        ))}
      </section>
      <section className="panel">
        <p className="eyebrow">Workflow trace</p>
        <h3>{mainNodes.length} node transitions · {toolSpans.length} tool spans</h3>
        <ol className="trace-list">
          {mainNodes.map((t, i) => (
            <li key={`${t.node}-${i}`}>
              <span className="node">{t.node.toLowerCase()}</span>
              <span className="outcome">{t.outcome}</span>
              <span className="ms">{t.durationMs} ms</span>
              {t.attributes && <details><summary>Decision details</summary><pre>{JSON.stringify(t.attributes, null, 2)}</pre></details>}
            </li>
          ))}
        </ol>
        {toolSpans.length > 0 && (
          <details>
            <summary>Tool spans</summary>
            <ol className="trace-list">
              {toolSpans.map((t, i) => (
                <li key={`${t.subNode}-${i}`}><span className="node">{t.subNode}</span><span className="outcome">{t.outcome}</span><span className="ms">{t.durationMs} ms</span>
                  {t.attributes && <details><summary>Evidence and filters</summary><pre>{JSON.stringify(t.attributes, null, 2)}</pre></details>}
                </li>
              ))}
            </ol>
          </details>
        )}
      </section>
    </>
  )
}
