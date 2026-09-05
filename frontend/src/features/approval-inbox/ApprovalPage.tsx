import { useState } from 'react'
import type { ApprovalDecisionRequest, ApprovalDecisionResponse, ApprovalView, ExecutionReceipt } from '../../core/contracts'
import { formatInstant } from '../../shared/format'
import { EmptyPanel, ErrorPanel } from '../../shared/StatePanels'

type Props = {
  approval: ApprovalView | null
  receipt: ExecutionReceipt | null
  result: ApprovalDecisionResponse | null
  busy: boolean
  error: { status?: number; message: string } | null
  onDecide: (request: ApprovalDecisionRequest) => void
}

/** Approval preview and approve / reject / edit with explicit consequence, scope, evidence timestamp and result states. */
export function ApprovalPage({ approval, receipt, result, busy, error, onDecide }: Props) {
  const [comment, setComment] = useState('')
  const [watchDays, setWatchDays] = useState(approval?.scope.watchDays ?? '7')
  const [editing, setEditing] = useState(false)
  if (!approval) {
    return <EmptyPanel title="No approval pending" body="This run did not request an approval. Healthy and report-only runs never raise one." />
  }
  // Expiry is decided by the backend at decision time (the demo replays historical as-of dates); the UI only reflects the returned status.
  const expired = approval.status === 'EXPIRED' || result?.workflowStatus === 'EXPIRED'
  const status = result?.approvalStatus ?? approval.status
  const finalStep = result?.workflowStatus
  const effectiveReceipt = result?.receipt ?? receipt
  return (
    <>
      <section className="panel approval">
        <p className="eyebrow">Approval preview</p>
        <h3>{approval.title}</h3>
        <p>{approval.rationale}</p>
        <dl className="kv">
          <div><dt>Action type</dt><dd>{approval.actionType}</dd></div>
          <div><dt>Consequence</dt><dd>{approval.consequence}</dd></div>
          <div><dt>Scope</dt><dd>{Object.entries(approval.scope).map(([k, v]) => `${k} = ${k === 'watchDays' && editing ? watchDays : v}`).join(' · ')}</dd></div>
          <div><dt>Evidence version</dt><dd><code>{approval.evidenceVersion}</code></dd></div>
          <div><dt>Evidence timestamp</dt><dd>{formatInstant(approval.evidenceTimestamp)}</dd></div>
          <div><dt>Requested</dt><dd>{formatInstant(approval.createdAt)}</dd></div>
          <div><dt>Expires</dt><dd>{formatInstant(approval.expiresAt)}{expired ? ' · expired' : ''}</dd></div>
          <div><dt>Status</dt><dd className={`pill pill--${status.toLowerCase()}`}>{expired ? 'EXPIRED' : status}</dd></div>
        </dl>
        <p className="muted">After approval the workflow re-checks authorization, evidence version, expiry and action state, executes exactly one idempotent mock effect, and appends audit events. Nothing executes before that.</p>

        {status === 'PENDING' && !expired && !result && (
          <form className="decision" onSubmit={(e) => e.preventDefault()}>
            <label>
              Comment
              <input value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Optional note for the audit trail" maxLength={1000} />
            </label>
            <label className="inline">
              <input type="checkbox" checked={editing} onChange={(e) => setEditing(e.target.checked)} /> Narrow scope before approving
            </label>
            {editing && (
              <label>
                Watch days
                <input type="number" min={1} max={7} value={watchDays} onChange={(e) => setWatchDays(e.target.value)} />
              </label>
            )}
            <div className="buttons">
              <button type="button" className="primary" disabled={busy} onClick={() => onDecide(editing ? { decision: 'EDIT', comment, editedScope: { watchDays } } : { decision: 'APPROVE', comment })}>
                {busy ? 'Revalidating…' : editing ? 'Approve edited scope' : 'Approve'}
              </button>
              <button type="button" className="danger" disabled={busy} onClick={() => onDecide({ decision: 'REJECT', comment })}>Reject</button>
            </div>
          </form>
        )}
        {expired && <p className="pill pill--expired">This approval expired before a decision; the workflow will not execute it. Run the brief again to obtain fresh evidence.</p>}
      </section>

      {error && <ErrorPanel status={error.status} message={error.message} />}

      {(result || effectiveReceipt || (status !== 'PENDING' && !expired)) && (
        <section className={`panel result result--${(finalStep ?? (effectiveReceipt?.status ?? status)).toLowerCase()}`} role="status">
          <p className="eyebrow">Result</p>
          <h3>
            {finalStep === 'EXECUTED' || effectiveReceipt?.status === 'EXECUTED'
              ? 'Executed once, audited'
              : finalStep === 'REJECTED' || status === 'REJECTED'
                ? 'Rejected — nothing executed'
                : finalStep === 'EXPIRED'
                  ? 'Expired — nothing executed'
                  : 'Approved but not executed'}
          </h3>
          {effectiveReceipt && (
            <dl className="kv">
              <div><dt>Receipt status</dt><dd>{effectiveReceipt.status}</dd></div>
              <div><dt>Idempotency key</dt><dd><code>{effectiveReceipt.idempotencyKey}</code></dd></div>
              <div><dt>External reference</dt><dd>{effectiveReceipt.externalReference ?? '—'}</dd></div>
              <div><dt>Attempted</dt><dd>{formatInstant(effectiveReceipt.attemptedAt)}</dd></div>
              <div><dt>Completed</dt><dd>{formatInstant(effectiveReceipt.completedAt)}</dd></div>
              <div><dt>Message</dt><dd>{effectiveReceipt.message ?? '—'}</dd></div>
            </dl>
          )}
          {result && result.revalidation.length > 0 && (
            <ul className="caveats">
              {result.revalidation.map((r) => <li key={r}>Revalidation: {r}</li>)}
            </ul>
          )}
        </section>
      )}
    </>
  )
}
