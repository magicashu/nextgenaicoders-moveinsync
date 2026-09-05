import { useState } from 'react'
import { g1RunArtifact, AGENT_COLORS, NODE_AGENT, WORKFLOW_NODES } from '../../core/mockData'
import { useAppStore } from '../../core/store'
import type { WorkflowNode } from '../../core/mockData'

function ApprovalModal({ onConfirm, onCancel, action }: { onConfirm: () => void; onCancel: () => void; action: 'approve' | 'reject' }) {
  return (
    <div className="modal-overlay">
      <div className="modal">
        <h3>{action === 'approve' ? 'Confirm Approval' : 'Confirm Rejection'}</h3>
        <p>{action === 'approve'
          ? 'You are about to approve this action proposal. The watchlist will be created for pinnacle-Slc once connected to backend.'
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

export function DecisionBriefPage() {
  const { approvalState, setApprovalState, tenant } = useAppStore()
  const [modal, setModal] = useState<'approve' | 'reject' | null>(null)
  const run = g1RunArtifact

  const handleConfirm = () => {
    if (modal) setApprovalState(modal === 'approve' ? 'approved' : 'rejected')
    setModal(null)
  }

  const completedNodes = WORKFLOW_NODES as unknown as WorkflowNode[]

  return (
    <div>
      {modal && <ApprovalModal action={modal} onConfirm={handleConfirm} onCancel={() => setModal(null)} />}

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
        <h1 className="page-title">Decision Brief</h1>
        <span style={{
          padding: '4px 12px', borderRadius: 999, fontSize: '0.75rem', fontWeight: 800,
          background: approvalState === 'pending' ? 'rgba(245,158,11,0.15)' : approvalState === 'approved' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
          color: approvalState === 'pending' ? 'var(--yellow)' : approvalState === 'approved' ? 'var(--green)' : 'var(--red)',
          border: `1px solid`,
        }}>
          {approvalState === 'pending' ? 'AWAITING APPROVAL' : approvalState === 'approved' ? 'APPROVED' : 'REJECTED'}
        </span>
      </div>
      <p className="page-subtitle">{tenant} · as of {run.asOfDate}</p>

      {run.evidence.caveats.map((c, i) => (
        <div key={i} className="caveat-ribbon" style={{ marginBottom: 12 }}>⚠ {c}</div>
      ))}

      <div className="hero-banner" style={{ marginBottom: 20 }}>
        <div className="hero-badge">G1 · Delay Spike Confirmed</div>
        <div className="hero-headline" style={{ marginTop: 10 }}>{run.headline}</div>
        <div className="hero-sub" style={{ marginTop: 6 }}>
          Period {run.metric.periodStart} → {run.metric.periodEnd} · {run.metric.denominator.toLocaleString()} eligible trips · {run.metric.contractVersion}
        </div>
      </div>

      <div className="two-col" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-header"><div className="card-title">Key Findings</div></div>
          <div className="card-body">
            {run.findings.map((f, i) => (
              <div key={i} style={{ display: 'flex', gap: 10, marginBottom: 14, alignItems: 'flex-start' }}>
                <div style={{ minWidth: 22, height: 22, borderRadius: '50%', background: 'rgba(6,182,212,0.15)', border: '1px solid rgba(6,182,212,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, color: 'var(--accent)', fontWeight: 900, flexShrink: 0 }}>{i + 1}</div>
                <div style={{ fontSize: '0.87rem', color: 'var(--text)', lineHeight: 1.65 }}>{f}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="action-card">
          <div className="card-title" style={{ marginBottom: 10 }}>Recommended Action</div>
          <h3>{run.recommendedAction.title}</h3>
          <p>{run.recommendedAction.rationale}</p>
          <div style={{ marginBottom: 12, fontSize: '0.78rem', color: 'var(--text-dim)' }}>
            Action ID: <code style={{ color: 'var(--pink)' }}>{run.recommendedAction.actionId}</code> ·
            Type: <code style={{ color: 'var(--pink)' }}>{run.recommendedAction.type}</code>
          </div>
          {approvalState === 'pending' ? (
            <div className="action-buttons">
              <button className="btn btn-approve" onClick={() => setModal('approve')}>Approve</button>
              <button className="btn btn-reject" onClick={() => setModal('reject')}>Reject</button>
            </div>
          ) : (
            <div style={{ padding: '10px 14px', borderRadius: 10, background: approvalState === 'approved' ? 'rgba(16,185,129,0.1)' : 'rgba(239,68,68,0.1)', border: `1px solid`, borderColor: approvalState === 'approved' ? 'rgba(16,185,129,0.4)' : 'rgba(239,68,68,0.4)', color: approvalState === 'approved' ? 'var(--green)' : 'var(--red)', fontSize: '0.85rem', fontWeight: 800 }}>
              {approvalState === 'approved' ? '✓ Approved — pending backend connection' : '✗ Rejected — draft discarded'}
            </div>
          )}
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header"><div className="card-title">Workflow Steps Completed</div></div>
        <div className="card-body">
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {completedNodes.map((node, i) => {
              const agent = NODE_AGENT[node]
              const color = AGENT_COLORS[agent]
              return (
                <div key={node} style={{
                  padding: '5px 11px', borderRadius: 8, fontSize: '0.72rem', fontWeight: 700,
                  background: `${color}18`, border: `1px solid ${color}40`, color,
                }}>
                  {i + 1}. {node.replace(/_/g, ' ')}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">Trust Record</div></div>
        <div className="card-body">
          <table className="data-table">
            <thead>
              <tr>
                <th>Evidence ID</th><th>Metric</th><th>Coverage</th><th>Contract</th><th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="td-mono">{`pinnacle-Slc:m01:${run.asOfDate}`}</td>
                <td>{run.metric.metricId}</td>
                <td>{run.evidence.coverage.toLocaleString()} trips</td>
                <td className="td-mono">{run.metric.contractVersion}</td>
                <td><span style={{ color: 'var(--green)', fontWeight: 700 }}>VERIFIED</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
