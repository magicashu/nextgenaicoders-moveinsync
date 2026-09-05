import type { EvidenceBundle, Finding } from '../core/contracts'
import { plain } from '../core/presentation'
import { formatValue, metricLabel } from './format'

const topics: Record<string, string> = { vendor: 'Vendor patterns', site_shift_direction: 'Sites and shifts', delay_reason: 'Recorded delay reasons', cost_billing: 'Cost and billing', feedback: 'Passenger experience', tracking_safety_alerts: 'Safety and tracking', noshow_roster: 'Attendance and pickups' }
export function EvidenceSummary({ findings = [], caveats = [], evidence }: { findings?: Finding[]; caveats?: Finding[]; evidence?: EvidenceBundle | null }) {
  const groups = new Map<string, Finding[]>()
  findings.forEach(f => { const name = topics[f.worker] ?? 'What the records show'; groups.set(name, [...(groups.get(name) ?? []), f]) })
  const metrics = (evidence?.items ?? []).filter(e => e.unit !== 'COUNT' && Object.keys(e.filters).length === 0 && !e.evidenceId.endsWith(':share'))
    .filter((e, i, all) => all.findIndex(other => other.metricId === e.metricId) === i).slice(0, 8)
  const limitations = [...new Set([...caveats.map(c => c.text), ...(evidence?.caveats ?? [])])]
  return <details className="card evidence-summary"><summary>Supporting evidence · {findings.length} findings</summary>
    <div className="card-body">
      {[...groups].map(([name, values]) => <section key={name}><h3>{name}</h3>{values.map(f => <p key={f.claimId}>{plain(f.text)}</p>)}</section>)}
      {metrics.length > 0 && <details><summary>Key figures and comparison</summary><div style={{ overflowX: 'auto' }}><table className="data-table"><thead><tr><th>Measure</th><th>Selected period</th><th>Previous four weeks</th></tr></thead><tbody>{metrics.map(m => <tr key={m.evidenceId}><td>{metricLabel(m.metricId)}</td><td>{formatValue(m.value, m.unit)}</td><td>{formatValue(m.baselineValue, m.unit)}</td></tr>)}</tbody></table></div></details>}
      {limitations.length > 0 && <details style={{ marginTop: 16 }}><summary>What these records cannot tell us</summary>{limitations.map(c => <p key={c}>{plain(c)}</p>)}</details>}
      {!findings.length && !metrics.length && <p>No supporting detail is available for this report.</p>}
    </div>
  </details>
}
