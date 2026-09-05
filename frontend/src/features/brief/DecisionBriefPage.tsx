import { useAppStore } from '../../core/store'
import { EvidenceSummary } from '../../shared/EvidenceSummary'
import { plain, MANAGER_ROLES } from '../../core/presentation'
import { formatValue } from '../../shared/format'

export function DecisionBriefPage({ onIncidents }: { onIncidents: () => void }) {
  const { tenant, role, run } = useAppStore()
  if (!run) return <div className="card card-body">Open the dashboard to load your selected report.</div>
  const metric = run.operations.headlineKpi.metric
  return <div>
    <h1 className="page-title">Decision Brief</h1>
    <p className="page-subtitle">{MANAGER_ROLES[role]} · {tenant} · {metric.periodStart} to {metric.periodEnd}</p>
    <div className="hero-banner"><div className="hero-badge">{role === 'FACILITIES_HEAD' ? 'Leadership report' : 'Transport report'} · {run.status.replaceAll('_', ' ').toLowerCase()}</div>
      <h2>{plain(run.operations.headline)}</h2>
      <p>{formatValue(metric.value, metric.unit)} · {metric.metricName} · previous four weeks: {formatValue(metric.baselineValue, metric.unit)}</p>
    </div>
    <div className="two-col" style={{ marginTop: 20 }}>
      <div className="card card-body"><h3>What happened</h3>{run.leadership.narrative.map((p, i) => <p key={i} style={{ lineHeight: 1.8 }}>{plain(p)}</p>)}</div>
      <div className="action-card"><h3>What you can do next</h3>
        {role === 'LINE_MANAGER' ? <p>Review arrival and shift information with your transport manager. Incident responses are handled by transport and facilities managers.</p>
          : run.operations.approval ? <><h4>{plain(run.operations.approval.title)}</h4><p>{plain(run.operations.approval.rationale)}</p><button className="btn btn-primary" onClick={onIncidents}>Review incident response</button></>
          : <p>No response is awaiting your approval for this report.</p>}
      </div>
    </div>
    <EvidenceSummary findings={run.operations.findings} caveats={run.operations.caveats} evidence={run.evidence} />
  </div>
}
