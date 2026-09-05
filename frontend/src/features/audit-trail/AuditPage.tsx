import type { AuditResponse } from '../../core/contracts'
import { formatInstant } from '../../shared/format'

type Props = { audit: AuditResponse; traceUrl: string | null }

const HIDDEN_KEYS = new Set(['prompt', 'completion', 'reasoning', 'chainOfThought', 'raw'])

/** Append-only business audit timeline. Payloads are shown as key/value facts; no model reasoning is ever displayed. */
export function AuditPage({ audit, traceUrl }: Props) {
  return (
    <section className="panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Audit trail</p>
          <h3>{audit.count} append-only events for run {audit.runId.slice(0, 8)}…</h3>
        </div>
        <div className="trace">
          <span>Trace</span>
          <code>{audit.traceId}</code>
          {traceUrl && <a href={`${traceUrl.replace(/\/$/, '')}/trace/${encodeURIComponent(audit.traceId)}`} target="_blank" rel="noreferrer">Open in Langfuse</a>}
        </div>
      </div>
      <ol className="timeline">
        {audit.events.map((event) => (
          <li key={event.eventId} className={`event event--${event.eventType.toLowerCase()}`}>
            <time>{formatInstant(event.occurredAt)}</time>
            <strong>{event.eventType.replace(/_/g, ' ').toLowerCase()}</strong>
            <dl className="kv compact">
              {Object.entries(event.payload).filter(([k]) => !HIDDEN_KEYS.has(k)).map(([k, v]) => (
                <div key={k}><dt>{k}</dt><dd>{v}</dd></div>
              ))}
            </dl>
          </li>
        ))}
      </ol>
      <small>PostgreSQL is the system of record; Langfuse traces are diagnostics and never replace this ledger.</small>
    </section>
  )
}
