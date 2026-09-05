import type { CopilotApi } from '../core/api'
import type { ApprovalDecisionResponse, ApprovalStatus, ExecutionReceipt, MorningBriefResponse } from '../core/contracts'
import { APPROVAL_ID, ACTION_ID, RUN_ID, answeredQuestion, g1Audit, g1Brief, g2Brief, healthyBrief, refusedQuestion } from './fixtures'

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
let approvalStatus: ApprovalStatus = 'PENDING'
let receipt: ExecutionReceipt | null = null

function briefFor(businessUnit: string, asOf: string): MorningBriefResponse {
  if (businessUnit === 'catalyst-Sac') return healthyBrief
  if (businessUnit === 'vanta-Aus' || asOf >= '2026-07-15') return g2Brief
  const approval = g1Brief.operations.approval ? { ...g1Brief.operations.approval, status: approvalStatus } : null
  return { ...g1Brief, asOfDate: asOf, operations: { ...g1Brief.operations, approval, receipt }, status: receipt ? 'EXECUTED' : g1Brief.status }
}

/** Mock transport with the same contracts as the HTTP client. Approve/reject mutate in-memory state so every UI state is reachable. */
export const mockApi: CopilotApi = {
  async morningBrief(identity, asOf) {
    await delay(250)
    if (identity.businessUnit === 'vanta-Sea') throw Object.assign(new Error('DuckDB dataset unavailable for this tenant in mock mode'), { status: 503 })
    return briefFor(identity.businessUnit, asOf)
  },
  async startWorkflow(identity, asOf) {
    await delay(400)
    return briefFor(identity.businessUnit, asOf)
  },
  async getWorkflow(identity, workflowId) {
    await delay(150)
    if (workflowId !== RUN_ID && workflowId !== g2Brief.runId) throw Object.assign(new Error('No decision run with this id is visible to this tenant'), { status: 404 })
    return briefFor(identity.businessUnit, '2026-06-08')
  },
  async ask(identity, request) {
    await delay(300)
    const lower = request.question.toLowerCase()
    if (lower.includes('orbit-slc') || lower.includes('vanta-') || lower.includes('select ') || lower.includes('ignore')) return refusedQuestion
    return answeredQuestion(request.question)
  },
  async approvalPreview(_identity, approvalId) {
    await delay(100)
    if (approvalId !== APPROVAL_ID || !g1Brief.operations.approval) throw Object.assign(new Error('No approval with this id is visible to this tenant'), { status: 404 })
    return { ...g1Brief.operations.approval, status: approvalStatus }
  },
  async decide(_identity, approvalId, request) {
    await delay(500)
    if (approvalId !== APPROVAL_ID) throw Object.assign(new Error('No approval with this id is visible to this tenant'), { status: 404 })
    if (approvalStatus !== 'PENDING') throw Object.assign(new Error(`Approval is already ${approvalStatus}`), { status: 409 })
    approvalStatus = request.decision === 'APPROVE' ? 'APPROVED' : request.decision === 'REJECT' ? 'REJECTED' : 'EDITED'
    const executed = request.decision !== 'REJECT'
    receipt = executed
      ? { actionId: ACTION_ID, runId: RUN_ID, idempotencyKey: `${RUN_ID}:${ACTION_ID}`, status: 'EXECUTED', attemptedAt: '2026-06-08T08:04:12Z', completedAt: '2026-06-08T08:04:12Z', externalReference: 'WATCH-7f3a', message: 'Mock site shift watchlist created' }
      : null
    const response: ApprovalDecisionResponse = {
      approvalId,
      runId: RUN_ID,
      decision: request.decision,
      approvalStatus,
      workflowStatus: executed ? 'EXECUTED' : 'REJECTED',
      receipt,
      revalidation: executed ? ['revalidation passed (EXECUTED)'] : ['rejected (no execution)'],
      trust: { ...g1Brief.trust, finalStep: executed ? 'EXECUTED' : 'REJECTED' },
    }
    return response
  },
  async audit(identity, workflowId) {
    await delay(150)
    if (identity.businessUnit !== 'pinnacle-Slc' || workflowId !== RUN_ID) throw Object.assign(new Error('No decision run with this id is visible to this tenant'), { status: 404 })
    const events = approvalStatus === 'PENDING' ? g1Audit.events.slice(0, 3) : g1Audit.events.filter((e) => !(approvalStatus === 'REJECTED' && e.eventType === 'ACTION_EXECUTED')).map((e) => (e.eventType === 'APPROVAL_APPROVE' && approvalStatus === 'REJECTED' ? { ...e, eventType: 'APPROVAL_REJECT' } : e))
    return { ...g1Audit, events, count: events.length }
  },
}

/** Test hook to reset mock state between scenarios. */
export function resetMockState() {
  approvalStatus = 'PENDING'
  receipt = null
}
