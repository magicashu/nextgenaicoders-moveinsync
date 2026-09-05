import { useState } from 'react'
import { useAppStore } from '../../core/store'
import { fetchInvestigation, type InvestigateResponse } from '../../core/api'
import { DateRangePicker } from '../../shared/DateRangePicker'
import { AGENT_COLORS } from '../../core/mockData'

function ApprovalModal({ onConfirm, onCancel, action }: { onConfirm: () => void; onCancel: () => void; action: 'approve' | 'reject' }) {
  return (
    <div className="modal-overlay">
      <div className="modal">
        <h3>{action === 'approve' ? 'Confirm Approval' : 'Confirm Rejection'}</h3>
        <p>{action === 'approve'
          ? 'You are about to approve this action proposal. The watchlist will be created once the durable action service is connected.'
          : 'You are about to reject this action proposal. The draft will be discarded.'}</p>
        <div className="modal-buttons">
          <button className="btn btn-secondary" onClick={onCancel}>Cancel</button>
          <button className={`btn ${action === 'approve' ? 'btn-approve' : 'btn-reject'}`} onClick={onConfirm}>
            {action === 'approve' ? 'Approve' : 'Reject'}
          </button>
        </div>
      </div>
    </div>
  )
}

const PIPELINE_STEPS = [
  { label: 'Supervisor', desc: 'Scopes the investigation and detects anomalies', color: AGENT_COLORS.supervisor },
  { label: 'Investigator', desc: 'Queries metrics across vendors, sites, and shifts', color: AGENT_COLORS.investigator },
  { label: 'Critic', desc: 'Verifies evidence integrity and cross-validates claims', color: AGENT_COLORS.critic },
  { label: 'Briefing', desc: 'Composes the decision brief and proposes actions', color: AGENT_COLORS.briefing },
]

function EmptyState() {
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ padding: '28px 24px', background: 'var(--bg2)', borderRadius: 16, border: '1px solid var(--border)', marginBottom: 16, textAlign: 'center' }}>
        <div style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--text)', marginBottom: 6 }}>Generate a Decision Brief</div>
        <div style={{ fontSize: '0.83rem', color: 'var(--text-dim)', maxWidth: 420, margin: '0 auto' }}>
          Select a date range and click <strong>Generate Brief</strong> to run the full 4-agent pipeline and produce an evidence-backed decision brief.
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 0, borderRadius: 14, overflow: 'hidden', border: '1px solid var(--border)' }}>
        <div style={{ padding: '12px 18px', background: 'var(--bg2)', borderBottom: '1px solid var(--border)' }}>
          <div style={{ fontSize: '0.72rem', fontWeight: 800, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.08em' }}>4-Agent Pipeline Preview</div>
        </div>
        {PIPELINE_STEPS.map((step, i) => (
          <div key={step.label} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 18px', borderBottom: i < PIPELINE_STEPS.length - 1 ? '1px solid var(--border)' : 'none', background: 'var(--bg)' }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
              background: `${step.color}18`, border: `1px solid ${step.color}44`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '0.72rem', fontWeight: 900, color: step.color,
            }}>
              {i + 1}
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 800, color: step.color, marginBottom: 2 }}>{step.label}</div>
              <div style={{ fontSize: '0.77rem', color: 'var(--text-dim)' }}>{step.desc}</div>
            </div>
            {i < PIPELINE_STEPS.length - 1 && (
              <div style={{ color: 'var(--border)', fontSize: 18 }}>→</div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

function LoadingState() {
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 0, borderRadius: 14, overflow: 'hidden', border: '1px solid var(--border)' }}>
        {PIPELINE_STEPS.map((step, i) => (
          <div key={step.label} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 18px', borderBottom: i < PIPELINE_STEPS.length - 1 ? '1px solid var(--border)' : 'none', background: 'var(--bg)' }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
              border: `2px solid ${step.color}44`, borderTopColor: step.color,
              animation: 'spin 1.2s linear infinite',
            }} />
            <div>
              <div style={{ fontSize: '0.85rem', fontWeight: 800, color: step.color, marginBottom: 2 }}>{step.label} agent running…</div>
              <div style={{ fontSize: '0.77rem', color: 'var(--text-dim)' }}>{step.desc}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function DecisionBriefPage() {
  const { approvalState, setApprovalState, tenant } = useAppStore()
  const [modal, setModal] = useState<'approve' | 'reject' | null>(null)
  const [dateRange, setDateRange] = useState({ from: '2026-06-01', to: '2026-06-30' })
  const [brief, setBrief] = useState<InvestigateResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = () => {
    if (modal) setApprovalState(modal === 'approve' ? 'approved' : 'rejected')
    setModal(null)
  }

  const loadBrief = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await fetchInvestigation(tenant, 'M01_DELAYED_TRIP_RATE', dateRange.from, dateRange.to)
      setBrief(data)
      setApprovalState('pending')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }

  const statusBadge = brief ? (
    <span style={{
      padding: '4px 12px', borderRadius: 999, fontSize: '0.72rem', fontWeight: 800,
      background: approvalState === 'pending' ? 'rgba(245,158,11,0.15)' : approvalState === 'approved' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
      color: approvalState === 'pending' ? 'var(--yellow)' : approvalState === 'approved' ? 'var(--green)' : 'var(--red)',
      border: '1px solid currentColor',
    }}>
      {approvalState === 'pending' ? 'AWAITING APPROVAL' : approvalState === 'approved' ? 'APPROVED' : 'REJECTED'}
    </span>
  ) : null

  return (
    <div>
      {modal && <ApprovalModal action={modal} onConfirm={handleConfirm} onCancel={() => setModal(null)} />}

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8, flexWrap: 'wrap' }}>
        <h1 className="page-title">Decision Brief</h1>
        {statusBadge}
      </div>

      <div className="filter-bar" style={{ marginBottom: 16 }}>
        <div className="filter-group">
          <span className="filter-label">Tenant</span>
          <span className="filter-value">{tenant}</span>
        </div>
        <div className="filter-divider" />
        <DateRangePicker value={dateRange} onChange={setDateRange} />
        <button className="filter-apply-btn" type="button" onClick={loadBrief} disabled={loading}>
          {loading ? 'Running…' : 'Generate Brief'}
        </button>
      </div>

      {error && (
        <div className="caveat-ribbon" style={{ background: '#FFF0F0', borderColor: '#FFCCCC', color: '#B00020', marginBottom: 12 }}>
          ⚠ {error}
        </div>
      )}

      {!brief && !loading && <EmptyState />}
      {loading && <LoadingState />}

      {brief && (
        <>
          {/* Hero banner */}
          <div className="hero-banner" style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
              <div style={{ flex: 1 }}>
                <div className="hero-badge">
                  {brief.verificationStatus === 'VERIFIED' ? '✓ Verified' : brief.verificationStatus === 'QUALIFIED' ? '~ Qualified' : '✗ ' + brief.verificationStatus}
                  &nbsp;·&nbsp;M01 Delayed Trip Rate
                </div>
                <div className="hero-headline">
                  {brief.leadershipSummary || `${tenant}: delayed-trip rate analysis for ${brief.dateFrom} → ${brief.dateTo}`}
                </div>
                <div className="hero-sub">
                  {tenant} · {brief.dateFrom} → {brief.dateTo} · {brief.primaryEvidence?.denominator?.toLocaleString() ?? '—'} eligible trips
                </div>
              </div>
              {brief.primaryEvidence?.value != null && (
                <div className="hero-confidence">
                  <div className="conf-val">{brief.primaryEvidence.value.toFixed(1)}%</div>
                  <div className="conf-label">delayed rate</div>
                </div>
              )}
            </div>
          </div>

          <div className="two-col" style={{ marginBottom: 16 }}>
            {/* Findings */}
            <div className="card">
              <div className="card-header">
                <div className="card-title">Key Findings</div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>{brief.findings.length} findings</div>
              </div>
              <div className="card-body">
                {brief.findings.length === 0 ? (
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    No verified findings for this window — the critic agent requires consistent evidence across the period.
                  </p>
                ) : brief.findings.map((f, i) => (
                  <div key={i} style={{ display: 'flex', gap: 10, marginBottom: 14, alignItems: 'flex-start' }}>
                    <div style={{
                      minWidth: 22, height: 22, borderRadius: '50%',
                      background: 'rgba(6,182,212,0.12)', border: '1px solid rgba(6,182,212,0.3)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 11, color: 'var(--accent)', fontWeight: 900, flexShrink: 0,
                    }}>{i + 1}</div>
                    <div style={{ fontSize: '0.87rem', color: 'var(--text)', lineHeight: 1.65 }}>{f}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Action */}
            <div className="action-card">
              <div className="card-title" style={{ marginBottom: 10 }}>
                {brief.proposedActions.length > 0 ? 'Recommended Action' : 'Operational Summary'}
              </div>
              {brief.proposedActions.length > 0 ? (
                <>
                  <div style={{ display: 'inline-flex', padding: '3px 10px', borderRadius: 999, marginBottom: 10, fontSize: '0.7rem', fontWeight: 800, background: 'rgba(6,182,212,0.12)', color: 'var(--accent)', border: '1px solid rgba(6,182,212,0.3)' }}>
                    {brief.proposedActions[0].type}
                  </div>
                  <h3 style={{ marginBottom: 8, marginTop: 0 }}>{brief.proposedActions[0].title}</h3>
                  <p style={{ color: 'var(--text-dim)', fontSize: '0.85rem', lineHeight: 1.65, marginBottom: 16 }}>{brief.proposedActions[0].rationale}</p>
                  {approvalState === 'pending' ? (
                    <div className="action-buttons">
                      <button className="btn btn-approve" onClick={() => setModal('approve')}>✓ Approve</button>
                      <button className="btn btn-reject" onClick={() => setModal('reject')}>✗ Reject</button>
                    </div>
                  ) : (
                    <div style={{
                      padding: '10px 14px', borderRadius: 10,
                      background: approvalState === 'approved' ? 'rgba(16,185,129,0.1)' : 'rgba(239,68,68,0.1)',
                      border: '1px solid',
                      borderColor: approvalState === 'approved' ? 'rgba(16,185,129,0.4)' : 'rgba(239,68,68,0.4)',
                      color: approvalState === 'approved' ? 'var(--green)' : 'var(--red)',
                      fontSize: '0.85rem', fontWeight: 800,
                    }}>
                      {approvalState === 'approved' ? '✓ Approved — pending durable action service' : '✗ Rejected — draft discarded'}
                    </div>
                  )}
                </>
              ) : (
                <p style={{ fontSize: '0.87rem', color: 'var(--text-dim)', lineHeight: 1.7 }}>
                  {brief.operationalSummary || 'Investigation complete. No action proposals generated for this window.'}
                </p>
              )}
            </div>
          </div>

          {/* Evidence Trust Record */}
          {brief.allEvidence.length > 0 && (
            <div className="card" style={{ marginBottom: 16 }}>
              <div className="card-header">
                <div className="card-title">Evidence Trust Record</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{brief.allEvidence.length} items collected</div>
              </div>
              <div className="card-body" style={{ padding: 0 }}>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Metric</th>
                      <th style={{ textAlign: 'right' }}>Value</th>
                      <th style={{ textAlign: 'right' }}>Coverage</th>
                      <th className="td-mono">Evidence ID</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {brief.allEvidence.map((e, i) => (
                      <tr key={i}>
                        <td style={{ fontWeight: 700 }}>{e.metricVersion.split('-v')[0]}</td>
                        <td style={{ textAlign: 'right', fontWeight: 700 }}>
                          {e.value != null ? `${e.value.toFixed(1)}%` : '—'}
                        </td>
                        <td style={{ textAlign: 'right', color: 'var(--text-dim)' }}>
                          {(e.denominator ?? e.population)?.toLocaleString() ?? '—'}
                        </td>
                        <td className="td-mono" style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>
                          {e.evidenceId.replace('ev-', '').slice(0, 16)}…
                        </td>
                        <td>
                          <span style={{
                            padding: '2px 8px', borderRadius: 999, fontSize: '0.72rem', fontWeight: 700,
                            background: e.status === 'AVAILABLE' ? 'rgba(16,185,129,0.1)' : 'rgba(245,158,11,0.1)',
                            color: e.status === 'AVAILABLE' ? 'var(--green)' : 'var(--yellow)',
                          }}>
                            {e.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {brief.caveats.map((c, i) => (
            <div key={i} className="caveat-ribbon" style={{ marginTop: 8 }}>⚠ {c}</div>
          ))}
        </>
      )}
    </div>
  )
}
