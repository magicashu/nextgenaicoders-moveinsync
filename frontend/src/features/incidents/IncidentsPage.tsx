import { useEffect, useState } from 'react'
import { httpApi } from '../../core/api'
import type { ApprovalView, MorningBriefResponse, AuditEvent } from '../../core/contracts'
import { identityFor, useAppStore } from '../../core/store'
import { labelForScope, plain } from '../../core/presentation'
import { EvidenceSummary } from '../../shared/EvidenceSummary'

const visibleScope = new Set(['businessUnit', 'site', 'sites', 'site_id', 'shift', 'shifts', 'shift_id', 'direction', 'vendor', 'vendor_id', 'windowDays', 'durationDays', 'windowEnd', 'watchDays'])
const editableScope = new Set(['site', 'sites', 'site_id', 'shift', 'shifts', 'shift_id', 'direction'])
function responseStatus(run: MorningBriefResponse) {
  if (run.operations.receipt?.status === 'EXECUTED') return 'Response recorded (demo)'
  if (run.operations.receipt?.status === 'APPROVED_NOT_EXECUTED') return 'Response could not be completed'
  const approval = run.operations.approval
  if (approval?.status === 'PENDING' && Date.parse(approval.expiresAt) <= Date.now()) return 'Review expired'
  return ({ PENDING: 'Needs your review', APPROVED: 'Approved', EDITED: 'Approved with changes', REJECTED: 'Dismissed', EXPIRED: 'Review expired' } as Record<string, string>)[approval?.status ?? ''] ?? 'Report only'
}
export function IncidentsPage() {
  const { tenant, role, run, captures } = useAppStore()
  const reports = [...new Map([...(run ? [run] : []), ...Object.values(captures).map(c => c.run)]
    .filter(r => r.businessUnit === tenant && r.persona === role && r.operations.approval)
    .map(r => [r.operations.approval!.approvalId, r])).values()]
  const [selected, setSelected] = useState<string | null>(null)
  const current = reports.find(r => r.runId === selected) ?? reports[0]
  return <div>
    <h1 className="page-title">Incident responses</h1>
    <p className="page-subtitle">Review issues and choose a response for {tenant}. Incidents shown here come from your saved reports.</p>
    {role === 'LINE_MANAGER' ? <div className="card card-body">Your view is read-only. Contact your transport manager to investigate an issue or approve a response.</div>
      : !current ? <div className="card card-body"><h3>No responses awaiting review</h3><p>Your captured reports have no incident response proposals. Check the dashboard for performance and arrival information.</p></div>
      : <><div className="suggestion-row">{reports.map(r => <button className={current.runId === r.runId ? 'btn btn-primary' : 'btn btn-secondary'} key={r.runId} onClick={() => setSelected(r.runId)}>{r.asOfDate} · {responseStatus(r)}</button>)}</div>
        <IncidentDetail key={current.runId} report={current} /></>}
  </div>
}
function IncidentDetail({ report }: { report: MorningBriefResponse }) {
  const { tenant, epoch } = useAppStore()
  const [approval, setApproval] = useState<ApprovalView>(report.operations.approval!)
  const [option, setOption] = useState<'APPROVE' | 'EDIT' | 'REJECT'>('APPROVE')
  const [comment, setComment] = useState('')
  const [scope, setScope] = useState<Record<string, string>>({})
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [activity, setActivity] = useState<AuditEvent[]>([])
  const [activityError, setActivityError] = useState(false)
  useEffect(() => {
    let active = true
    httpApi.audit(identityFor(tenant), report.runId).then(result => {
      if (active) { setActivity(result.events.filter(e => ['APPROVAL_APPROVE', 'APPROVAL_EDIT', 'APPROVAL_REJECT', 'ACTION_EXECUTED', 'ACTION_NOT_EXECUTED'].includes(e.eventType))); setActivityError(false) }
    }).catch(() => { if (active) setActivityError(true) })
    return () => { active = false }
  }, [tenant, report.runId, report.operations.approval?.status])
  useEffect(() => {
    let active = true
    httpApi.approvalPreview(identityFor(tenant), approval.approvalId).then(value => {
      if (active) { setApproval(value); setScope(value.scope); setLoaded(true) }
    }).catch(() => { if (active) setError('Unable to check the latest response status. Reopen this incident to retry.') })
    return () => { active = false }
  }, [tenant, approval.approvalId])
  const pending = loaded && approval.status === 'PENDING' && Date.parse(approval.expiresAt) > Date.now()
  const editable = Object.entries(approval.scope).filter(([key, value]) => editableScope.has(key) && value.split(',').length > 1)
  const validEdit = editable.some(([key, value]) => scope[key] !== value && Boolean(scope[key]))
    && editable.every(([key]) => Boolean(scope[key]))
  const decide = async () => {
    if (busy || !pending) return
    setBusy(true); setError(null)
    try {
      const decision = await httpApi.decide(identityFor(tenant), approval.approvalId, {
        decision: option, comment: comment.trim(), ...(option === 'EDIT' ? { editedScope: Object.fromEntries(editable.map(([key]) => [key, scope[key]])) } : {}),
      })
      if (useAppStore.getState().epoch !== epoch) return
      const updated = { ...report, status: decision.workflowStatus, trust: decision.trust,
        operations: { ...report.operations, receipt: decision.receipt, approval: { ...approval, status: decision.approvalStatus, scope: option === 'EDIT' ? scope : approval.scope } } }
      useAppStore.getState().updateCapture(updated, epoch)
      setApproval(updated.operations.approval); setConfirm(false)
      try { useAppStore.getState().updateCapture(await httpApi.getWorkflow(identityFor(tenant), report.runId), epoch) }
      catch { setError('Your decision was saved. Reopen this incident to retrieve its latest details.') }
    } catch { setError('The response could not be saved. It may have expired or already been reviewed. Reopen the incident to check before retrying.') }
    finally { setBusy(false) }
  }
  return <>
    <div className="hero-banner"><span className="hero-badge">{responseStatus({ ...report, operations: { ...report.operations, approval } })}</span><h2>{approval.status === 'EDITED' ? 'Response approved with adjusted scope' : plain(approval.title)}</h2><p>{plain(approval.rationale)}</p></div>
    {error && <p className="caveat-ribbon" role="alert">{error}</p>}
    <div className="two-col" style={{ marginTop: 20 }}>
      <div className="card card-body"><h3>What needs attention</h3><p>{plain(report.operations.headline)}</p>
        <h4>Response scope</h4><dl className="scope-list">{Object.entries(approval.scope).filter(([key]) => visibleScope.has(key)).map(([key, value]) => <div key={key} style={{ display: 'contents' }}><dt>{labelForScope(key)}</dt><dd>{value}</dd></div>)}</dl>
        <p>Records captured: {new Date(approval.evidenceTimestamp).toLocaleString()}</p><p>Review by: {new Date(approval.expiresAt).toLocaleString()}</p>
      </div>
      <div className="card card-body"><h3>Choose how to respond</h3>
        <p>{plain(approval.consequence)}</p><p>This local product records a simulated response. It does not send messages, update transport systems or confirm that the underlying issue has been fixed.</p>
        {pending ? <><div className="resolution-options">
          <label><input type="radio" name="response" checked={option === 'APPROVE'} onChange={() => setOption('APPROVE')} />Approve the recommended response</label>
          {editable.length > 0 && <label><input type="radio" name="response" checked={option === 'EDIT'} onChange={() => setOption('EDIT')} />Narrow the affected sites or shifts, then approve</label>}
          <label><input type="radio" name="response" checked={option === 'REJECT'} onChange={() => setOption('REJECT')} />Dismiss this response with a reason</label>
        </div>
        {option === 'EDIT' && editable.map(([key, value]) => <fieldset key={key}><legend>{labelForScope(key)}</legend>{value.split(',').map(v => v.trim()).map(value => <label key={value} style={{ display: 'block' }}><input type="checkbox" checked={(scope[key] ?? '').split(',').includes(value)} onChange={e => setScope(s => ({ ...s, [key]: e.target.checked ? [...s[key].split(',').filter(Boolean), value].join(',') : s[key].split(',').filter(v => v !== value).join(',') }))} />{value}</label>)}</fieldset>)}
        <label htmlFor="resolution-note">Decision note {option !== 'APPROVE' ? '(required)' : '(optional)'}</label>
        <textarea id="resolution-note" rows={3} maxLength={500} value={comment} onChange={e => setComment(e.target.value)} placeholder="Explain the follow-up or why this response is not needed." />
        <button className="btn btn-primary" disabled={busy || (option !== 'APPROVE' && !comment.trim()) || (option === 'EDIT' && !validEdit)} onClick={() => setConfirm(true)}>Review my decision</button>
        </> : <p>{loaded ? 'This proposal is no longer awaiting a decision. Capture a new report if you need a fresh review.' : 'Checking current response status…'}</p>}
      </div>
    </div>
    <EvidenceSummary findings={report.operations.findings} caveats={report.operations.caveats} evidence={report.evidence} />
    <details className="card evidence-summary"><summary>Response history</summary><div className="card-body">
      {activityError ? <p>Response history is temporarily unavailable.</p> : !activity.length ? <p>No response decision has been recorded yet.</p> : activity.map(event => <p key={event.eventId}><strong>{({ APPROVAL_APPROVE: 'Approved', APPROVAL_EDIT: 'Approved with adjusted scope', APPROVAL_REJECT: 'Dismissed', ACTION_EXECUTED: 'Simulated response recorded', ACTION_NOT_EXECUTED: 'Response not completed' } as Record<string, string>)[event.eventType]}</strong> · {new Date(event.occurredAt).toLocaleString()}{event.payload.comment && event.payload.comment !== 'null' && <><br />{event.payload.comment}</>}</p>)}
    </div></details>
    {confirm && <div className="modal-overlay"><div className="modal" role="dialog" aria-modal="true" aria-labelledby="response-confirm-title">
      <h3 id="response-confirm-title">{option === 'REJECT' ? 'Dismiss this response?' : 'Confirm this response?'}</h3>
      <p>{option === 'REJECT' ? 'The proposal will be dismissed and your reason saved.' : plain(approval.consequence)}</p>
      <p>{plain(approval.title)}</p>
      {option === 'EDIT' && <dl className="scope-list">{editable.map(([key]) => <div key={key} style={{ display: 'contents' }}><dt>{labelForScope(key)}</dt><dd>{scope[key]}</dd></div>)}</dl>}
      {comment && <p>{comment}</p>}
      <div className="modal-buttons"><button className="btn btn-secondary" disabled={busy} onClick={() => setConfirm(false)}>Go back</button><button className="btn btn-primary" disabled={busy} onClick={() => void decide()}>{busy ? 'Saving…' : 'Confirm decision'}</button></div>
    </div></div>}
  </>
}
