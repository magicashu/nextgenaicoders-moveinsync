import { identityFor, useAppStore } from '../../core/store'
import { useState, useEffect } from 'react'
import { httpApi, ApiRequestError } from '../../core/api'
import type { AuditResponse } from '../../core/contracts'
import { labelForScope } from '../../core/presentation'

const eventLabel = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/^./, first => first.toUpperCase())

export function AuditPage() {
  const { tenant, role, run } = useAppStore()
  const [filter, setFilter] = useState('')
  const [audit, setAudit] = useState<AuditResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [reload, setReload] = useState(0)
  const [page, setPage] = useState(0)
  useEffect(() => {
    let active = true
    setAudit(null); setError(null); setPage(0); setFilter(''); setLoading(Boolean(run))
    if (run) httpApi.audit(identityFor(tenant, role), run.runId)
      .then(value => { if (active) setAudit(value) })
      .catch(error => {
        if (active) setError(error instanceof ApiRequestError && error.status === 404
          ? 'This capture is no longer available on the backend. Refresh the dashboard to create a new capture.'
          : 'Unable to load the audit trail. Try loading the events again.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [tenant, role, run, reload])
  const events = audit?.events.filter(e => (e.eventType + JSON.stringify(e.payload)).toLowerCase().includes(filter.toLowerCase())) ?? []
  const visible = events.slice(page * 50, (page + 1) * 50)

  return <div>
    <h1 className="page-title">Audit Trail</h1>
    <p className="page-subtitle">Recorded events for {tenant}{run ? ' · report as of ' + run.asOfDate : ''}. Loading events does not rerun the investigation.</p>
    {run && <div className="audit-toolbar">
      <input aria-label="Filter audit events" placeholder="Filter by event, decision or node…" value={filter}
        onChange={e => { setFilter(e.target.value); setPage(0) }} />
      <button className="btn btn-secondary" disabled={loading} onClick={() => setReload(value => value + 1)}>Reload events</button>
    </div>}
    {error && <div className="caveat-ribbon" role="alert">{error}</div>}
    {loading && <p role="status">Loading recorded audit events…</p>}
    {audit && <>
      <p className="page-subtitle">{audit.count} recorded events · {events.length} match the filter</p>
      <details className="audit-identifiers"><summary>Report and trace references</summary><dl className="scope-list"><dt>Run</dt><dd>{audit.runId}</dd><dt>Trace</dt><dd>{audit.traceId}</dd></dl></details>
      <div className="card card-body" style={{ overflowX: 'auto' }}>
        <table className="data-table"><thead><tr><th>Time</th><th>Event</th><th>Recorded details</th></tr></thead>
          <tbody>{visible.map(event => <tr key={event.eventId}>
            <td className="td-mono"><time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString()}</time></td>
            <td>{eventLabel(event.eventType)}{event.payload.node && <div className="audit-node">{eventLabel(event.payload.node)}</div>}</td>
            <td><details><summary>View details</summary><dl className="scope-list audit-details">{Object.entries(event.payload).map(([key, value]) => <div key={key}><dt>{labelForScope(key)}</dt><dd>{String(value)}</dd></div>)}</dl></details></td>
          </tr>)}</tbody>
        </table>
        {!events.length && <p>No matching events.</p>}
        {events.length > 50 && <div className="audit-toolbar">
          <button className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button>
          <span>Page {page + 1} of {Math.ceil(events.length / 50)}</span>
          <button className="btn btn-secondary" disabled={(page + 1) * 50 >= events.length} onClick={() => setPage(value => value + 1)}>Next</button>
        </div>}
      </div>
    </>}
    {!run && <div className="card card-body"><h3>No report selected</h3><p>Open Dashboard to load a report. Its recorded audit events will appear here.</p></div>}
  </div>
}
