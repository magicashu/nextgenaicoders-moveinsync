import { useState } from 'react'
import { identityFor, useAppStore } from '../../core/store'
import { httpApi } from '../../core/api'
import { briefView, startInvestigation, rangeFor, formatValue } from '../../core/dashboardApi'
import { DateRangePicker } from '../../shared/DateRangePicker'

function ApprovalModal({ onConfirm, onCancel, action, consequence, busy }: { onConfirm: () => void; onCancel: () => void; action: 'approve' | 'reject'; consequence: string; busy: boolean }) {
  return (
    <div className="modal-overlay">
      <div className="modal" role="dialog" aria-modal="true" aria-label="Confirm action decision">
        <h3>{action === 'approve' ? 'Confirm Approval' : 'Confirm Rejection'}</h3>
        <p>{action === 'approve'
          ? consequence
          : 'Reject this action proposal and record the decision in the audit trail.'}</p>
        <div className="modal-buttons">
          <button className="btn btn-secondary" onClick={onCancel} disabled={busy}>Cancel</button>
          <button className={`btn ${action === 'approve' ? 'btn-approve' : 'btn-reject'}`} onClick={onConfirm} disabled={busy}>
            {action === 'approve' ? 'Approve' : 'Reject'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function DecisionBriefPage() {
  const { tenant, run, epoch, busy } = useAppStore()
  const brief = run ? briefView(run) : null
  const approval = run?.operations.approval
  const approvalState = approval?.status === 'PENDING' ? 'pending' : approval?.status === 'APPROVED' || approval?.status === 'EDITED' ? 'approved' : 'rejected'
  const [modal, setModal] = useState<'approve' | 'reject' | null>(null)
  const [dateRange, setDateRange] = useState(run ? rangeFor(run.asOfDate) : { from: '2026-06-01', to: '2026-06-07' })
  const [error, setError] = useState<string | null>(null)
  const loading = busy

  const handleConfirm = async () => {
    if (!modal || !approval || !run || useAppStore.getState().busy) return
    useAppStore.setState({ busy: true })
    setError(null)
    try {
      const result = await httpApi.decide(identityFor(tenant), approval.approvalId, { decision: modal === 'approve' ? 'APPROVE' : 'REJECT' })
      // Preserve the confirmed decision even if the subsequent refresh is temporarily unavailable.
      useAppStore.getState().setRun({ ...run, status: result.workflowStatus, trust: result.trust,
        operations: { ...run.operations, status: result.workflowStatus, approval: { ...approval, status: result.approvalStatus }, receipt: result.receipt } }, epoch)
      setModal(null)
      useAppStore.getState().setRun(await httpApi.getWorkflow(identityFor(tenant), run.runId), epoch)
    } catch (e) { setError(e instanceof Error ? e.message : 'Decision failed') }
    finally { if (useAppStore.getState().epoch === epoch) useAppStore.setState({ busy: false }) }
  }

  const loadBrief = async () => {
    setError(null)
    try { await startInvestigation(tenant, dateRange.from, dateRange.to) }
    catch (e) { setError(e instanceof Error ? e.message : 'Investigation failed') }
  }

  return (
    <div>
      {modal && <ApprovalModal busy={busy} consequence={approval?.consequence ?? 'Approval resumes the governed workflow.'} action={modal} onConfirm={handleConfirm} onCancel={() => setModal(null)} />}

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8, flexWrap: 'wrap' }}>
        <h1 className="page-title">Decision Brief</h1>
        {brief && (
          <span style={{
            padding: '4px 12px', borderRadius: 999, fontSize: '0.75rem', fontWeight: 800,
            background: approvalState === 'pending' ? 'rgba(245,158,11,0.15)' : approvalState === 'approved' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
            color: approvalState === 'pending' ? 'var(--yellow)' : approvalState === 'approved' ? 'var(--green)' : 'var(--red)',
            border: '1px solid',
          }}>
            {approval?.status ?? run?.status}
          </span>
        )}
      </div>

      <p className="page-subtitle">{run ? `Run ${run.runId} · ${run.trust.modelId} · ${run.trust.modelCalls} model calls` : 'Select seven consecutive days; the baseline uses the preceding four weeks.'}</p>
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

      {!brief && !loading && (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 340, gap: 12, color: 'var(--text-dim)' }}>
          <div style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text)' }}>No brief generated</div>
          <div style={{ fontSize: '0.85rem', textAlign: 'center', maxWidth: 360 }}>
            Select a seven-day window and click <strong>Generate Brief</strong> to run the full agent pipeline.
          </div>
        </div>
      )}

      {loading && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 200, gap: 12 }}>
          <div style={{ width: 32, height: 32, border: '3px solid #E0E0E0', borderTopColor: '#3FA535', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
          <div style={{ fontSize: '0.9rem', color: 'var(--text-dim)' }}>Running agent pipeline…</div>
        </div>
      )}

      {brief && (
        <>
          {/* Hero */}
          <div className="hero-banner" style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div className="hero-badge">
                  {brief.verificationStatus.replaceAll('_', ' ')}
                  &nbsp;·&nbsp;{brief.metricName}
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
                  <div className="conf-val">{formatValue(brief.primaryEvidence.value, brief.primaryEvidence.unit)}</div>
                  <div className="conf-label">{brief.metricName}</div>
                </div>
              )}
            </div>
          </div>

          <div className="two-col" style={{ marginBottom: 16 }}>
            {/* Findings */}
            <div className="card">
              <div className="card-header"><div className="card-title">Key Findings</div></div>
              <div className="card-body">
                {brief.findings.length === 0 ? (
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    No verified findings for this window — the critic agent requires consistent evidence across the period.
                  </p>
                ) : brief.findings.map((f, i) => (
                  <div key={i} style={{ display: 'flex', gap: 10, marginBottom: 14, alignItems: 'flex-start' }}>
                    <div style={{ minWidth: 22, height: 22, borderRadius: '50%', background: 'rgba(6,182,212,0.15)', border: '1px solid rgba(6,182,212,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, color: 'var(--accent)', fontWeight: 900, flexShrink: 0 }}>{i + 1}</div>
                    <div style={{ fontSize: '0.87rem', color: 'var(--text)', lineHeight: 1.65 }}>{f}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Action / Summary */}
            <div className="action-card">
              <div className="card-title" style={{ marginBottom: 10 }}>
                {brief.proposedActions.length > 0 ? 'Recommended Action' : 'Operational Summary'}
              </div>
              {brief.proposedActions.length > 0 ? (
                <>
                  <h3>{brief.proposedActions[0].title}</h3>
                  <p>{brief.proposedActions[0].rationale}</p>
                  <div style={{ marginBottom: 12, fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                    Type: <code style={{ color: 'var(--pink)' }}>{brief.proposedActions[0].type}</code>
                  </div>
                  {approval && <details style={{ marginBottom: 16 }}><summary>Scope, evidence and expiry</summary><pre>{JSON.stringify({ scope: approval.scope, evidenceVersion: approval.evidenceVersion, evidenceTimestamp: approval.evidenceTimestamp, expiresAt: approval.expiresAt, consequence: approval.consequence }, null, 2)}</pre></details>}
                  {approval?.status === 'PENDING' ? (
                    <div className="action-buttons">
                      <button className="btn btn-approve" disabled={busy} onClick={() => setModal('approve')}>Approve</button>
                      <button className="btn btn-reject" disabled={busy} onClick={() => setModal('reject')}>Reject</button>
                    </div>
                  ) : (
                    <div style={{ padding: '10px 14px', borderRadius: 10, background: approvalState === 'approved' ? 'rgba(16,185,129,0.1)' : 'rgba(239,68,68,0.1)', border: '1px solid', borderColor: approvalState === 'approved' ? 'rgba(16,185,129,0.4)' : 'rgba(239,68,68,0.4)', color: approvalState === 'approved' ? 'var(--green)' : 'var(--red)', fontSize: '0.85rem', fontWeight: 800 }}>
                      {run?.operations.receipt?.message ?? approval?.status ?? run?.status}
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
                          {formatValue(e.value, e.unit)}
                        </td>
                        <td style={{ textAlign: 'right', color: 'var(--text-dim)' }}>
                          {(e.denominator ?? e.population)?.toLocaleString() ?? '—'}
                        </td>
                        <td className="td-mono" style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>
                          <details><summary>{e.evidenceId.slice(0, 16)}…</summary><pre>{JSON.stringify(e, null, 2)}</pre></details>
                        </td>
                        <td>
                          <span style={{ color: e.status === 'AVAILABLE' ? 'var(--green)' : 'var(--yellow)', fontWeight: 700, fontSize: '0.8rem' }}>
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
