import type { MorningBriefResponse } from '../../core/contracts'
import { EvidenceChips } from '../../shared/EvidenceChips'
import { formatValue } from '../../shared/format'

const WORKER_LABELS: Record<string, string> = {
  detector: 'Detection and benchmark',
  vendor: 'Vendor dispersion',
  site_shift_direction: 'Site, shift and direction',
  delay_reason: 'Delay reasons',
  cost_billing: 'Cost and billing',
  feedback: 'Rider feedback',
  tracking_safety_alerts: 'Tracking and safety alerts',
  noshow_roster: 'No-show and roster (leg level)',
  peers: 'Peer tenants (facilities head)',
  system: 'System caveats',
}

type Props = { brief: MorningBriefResponse; onOpenEvidence: (evidenceId: string) => void }

/** Drill-down across the seven investigation domains with capability greying and the full evidence table. */
export function InvestigationPage({ brief, onOpenEvidence }: Props) {
  const findings = [...brief.operations.findings, ...brief.operations.caveats]
  const workers = Array.from(new Set(findings.map((f) => f.worker)))
  const branches = brief.trust.branchStatus
  const gaps = brief.trust.capabilityGaps
  return (
    <>
      <section className="panel">
        <p className="eyebrow">Investigation branches</p>
        <h3>Seven governed workers, one evidence bundle</h3>
        <div className="branches">
          {Object.keys(WORKER_LABELS).filter((w) => !['detector', 'peers', 'system'].includes(w)).map((worker) => {
            const status = branches[worker]
            const gap = gaps.find((g) => g.toLowerCase().startsWith(worker.split('_')[0]))
            const unsupported = !status && gap !== undefined
            return (
              <div key={worker} className={`branch ${status === 'COMPLETE' ? 'branch--complete' : status ? 'branch--partial' : 'branch--off'}`}>
                <strong>{WORKER_LABELS[worker]}</strong>
                <span>{status ? status.toLowerCase() : unsupported ? 'unsupported' : 'not planned'}</span>
                {gap && <small>{gap}</small>}
              </div>
            )
          })}
        </div>
      </section>

      {workers.map((worker) => (
        <section key={worker} className="panel">
          <p className="eyebrow">{WORKER_LABELS[worker] ?? worker}</p>
          <ul className="findings">
            {findings.filter((f) => f.worker === worker).map((f) => (
              <li key={f.claimId} className={`finding finding--${f.kind.toLowerCase()}`}>
                <span className="kind">{f.kind.toLowerCase()}</span>
                <span>{f.text}</span>
                <EvidenceChips evidenceIds={f.evidenceIds} onOpen={onOpenEvidence} />
              </li>
            ))}
          </ul>
        </section>
      ))}

      <section className="panel">
        <p className="eyebrow">Evidence bundle</p>
        <h3>{brief.evidence.items.length} governed items · confidence {Math.round(brief.evidence.confidence * 100)}%</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Metric</th><th>Filters</th><th>Value</th><th>Baseline</th><th>Population</th><th>Window</th><th>Data</th><th></th></tr>
            </thead>
            <tbody>
              {brief.evidence.items.map((item) => (
                <tr key={item.evidenceId}>
                  <td>{item.metricId}</td>
                  <td>{Object.keys(item.filters).length === 0 ? 'tenant' : Object.entries(item.filters).map(([k, v]) => `${k}=${v}`).join(', ')}</td>
                  <td>{formatValue(item.value, item.unit)}</td>
                  <td>{item.baselineValue === null ? '—' : formatValue(item.baselineValue, item.unit)}</td>
                  <td>{item.numerator !== null && item.denominator !== null ? `${item.numerator.toLocaleString('en-IN')} / ${item.denominator.toLocaleString('en-IN')}` : item.supportingCount.toLocaleString('en-IN')}</td>
                  <td>{item.periodStart} → {item.periodEnd}</td>
                  <td>{item.dataVersion}</td>
                  <td><button type="button" className="link" onClick={() => onOpenEvidence(item.evidenceId)}>details</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}
