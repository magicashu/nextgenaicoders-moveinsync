import type { EvidenceBundle, EvidenceItem, TrustPanel } from '../core/contracts'
import { formatValue } from './format'
import { METRIC_DEFINITIONS } from './metricDefinitions'

type Props = { evidenceId: string | null; evidence: EvidenceBundle; trust: TrustPanel | null; onClose: () => void }

/** Governed evidence metadata for one item: definition, filters, population, window, freshness, confidence, caveats. */
export function EvidenceDrawer({ evidenceId, evidence, trust, onClose }: Props) {
  if (!evidenceId) return null
  const item: EvidenceItem | undefined = evidence.items.find((i) => i.evidenceId === evidenceId)
  const definition = item ? METRIC_DEFINITIONS[item.metricId] : undefined
  return (
    <aside className="drawer" role="dialog" aria-label="Evidence details">
      <header>
        <p className="eyebrow">Governed evidence</p>
        <h3>{definition?.name ?? item?.metricId ?? 'Evidence'}</h3>
        <button type="button" className="ghost" onClick={onClose} aria-label="Close evidence details">Close</button>
      </header>
      {!item && <p className="muted">This reference ({evidenceId}) is not part of the returned bundle; the API keeps rankings and distributions behind ids.</p>}
      {item && (
        <>
          <dl className="kv">
            <div><dt>Evidence id</dt><dd><code>{item.evidenceId}</code></dd></div>
            <div><dt>Value</dt><dd>{formatValue(item.value, item.unit)}</dd></div>
            <div><dt>Baseline</dt><dd>{item.baselineValue === null ? '—' : formatValue(item.baselineValue, item.unit)}</dd></div>
            <div><dt>Change</dt><dd>{item.delta === null ? '—' : `${item.delta > 0 ? '+' : ''}${item.unit === 'PERCENT' ? `${item.delta.toFixed(2)} pts` : formatValue(item.delta, item.unit)}`}</dd></div>
            <div><dt>Population</dt><dd>{item.numerator !== null && item.denominator !== null ? `${item.numerator.toLocaleString('en-IN')} of ${item.denominator.toLocaleString('en-IN')}` : `${item.supportingCount.toLocaleString('en-IN')} supporting`}</dd></div>
            <div><dt>Window</dt><dd>{item.periodStart} → {item.periodEnd}</dd></div>
            <div><dt>Filters</dt><dd>{Object.keys(item.filters).length === 0 ? 'tenant only' : Object.entries(item.filters).map(([k, v]) => `${k} = ${v}`).join(', ')}</dd></div>
            <div><dt>Contract</dt><dd>{item.contractVersion}</dd></div>
            <div><dt>Data version</dt><dd>{item.dataVersion}</dd></div>
            <div><dt>Source</dt><dd><code>{item.source}</code></dd></div>
          </dl>
          {definition && (
            <section className="definition">
              <p className="eyebrow">Definition (metrics-v1.1)</p>
              <ul>
                <li><strong>Numerator:</strong> {definition.numerator}</li>
                <li><strong>Denominator:</strong> {definition.denominator}</li>
                <li><strong>Exclusions:</strong> {definition.exclusions}</li>
                <li><strong>Grain:</strong> {definition.grain}</li>
              </ul>
            </section>
          )}
        </>
      )}
      <section>
        <p className="eyebrow">Bundle</p>
        <dl className="kv">
          <div><dt>Confidence</dt><dd>{Math.round(evidence.confidence * 100)}% (deterministic components)</dd></div>
          <div><dt>Coverage</dt><dd>{evidence.coverage.toLocaleString('en-IN')} in headline population</dd></div>
          {trust && <div><dt>Freshness</dt><dd>data {trust.dataVersion}, run {trust.latencyMs} ms</dd></div>}
        </dl>
        {evidence.caveats.length > 0 && (
          <ul className="caveats">
            {evidence.caveats.map((c) => <li key={c}>{c}</li>)}
          </ul>
        )}
      </section>
    </aside>
  )
}
