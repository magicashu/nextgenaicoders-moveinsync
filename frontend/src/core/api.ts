import type {
  ApiError,
  ApprovalDecisionRequest,
  ApprovalDecisionResponse,
  ApprovalView,
  AuditResponse,
  Identity,
  MorningBriefResponse,
  QuestionRequest,
  QuestionResponse,
} from './contracts'
import { identityHeaders } from './identity'
import { mockApi } from '../mocks/mockApi'

export class ApiRequestError extends Error {
  readonly status: number
  readonly error: ApiError | null

  constructor(status: number, error: ApiError | null, fallbackMessage: string) {
    super(error?.message ?? fallbackMessage)
    this.status = status
    this.error = error
  }
}

export type CopilotApi = {
  morningBrief(identity: Identity, asOf: string, persona?: string): Promise<MorningBriefResponse>
  startWorkflow(identity: Identity, asOf: string, persona?: string, refresh?: boolean): Promise<MorningBriefResponse>
  getWorkflow(identity: Identity, workflowId: string): Promise<MorningBriefResponse>
  ask(identity: Identity, request: QuestionRequest): Promise<QuestionResponse>
  approvalPreview(identity: Identity, approvalId: string): Promise<ApprovalView>
  decide(identity: Identity, approvalId: string, request: ApprovalDecisionRequest): Promise<ApprovalDecisionResponse>
  audit(identity: Identity, workflowId: string): Promise<AuditResponse>
}

async function call<T>(identity: Identity, path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...identityHeaders(identity), ...(init.headers ?? {}) },
  })
  if (!response.ok) {
    let parsed: ApiError | null = null
    try {
      parsed = (await response.json()) as ApiError
    } catch {
      parsed = null
    }
    throw new ApiRequestError(response.status, parsed, `Request failed with status ${response.status}`)
  }
  return (await response.json()) as T
}

/** Real HTTP client. Same business semantics as the mock; only transport differs. */
export const httpApi: CopilotApi = {
  morningBrief: (identity, asOf, persona) =>
    call(identity, `/api/v1/briefs/morning?asOf=${encodeURIComponent(asOf)}${persona ? `&persona=${encodeURIComponent(persona)}` : ''}`),
  startWorkflow: (identity, asOf, persona, refresh = false) =>
    call(identity, `/api/v1/workflows?refresh=${refresh}`, { method: 'POST', body: JSON.stringify({ asOfDate: asOf, persona: persona ?? null }) }),
  getWorkflow: (identity, workflowId) => call(identity, `/api/v1/workflows/${encodeURIComponent(workflowId)}`),
  ask: (identity, request) => call(identity, '/api/v1/questions', { method: 'POST', body: JSON.stringify(request) }),
  approvalPreview: (identity, approvalId) => call(identity, `/api/v1/approvals/${encodeURIComponent(approvalId)}`),
  decide: (identity, approvalId, request) =>
    call(identity, `/api/v1/approvals/${encodeURIComponent(approvalId)}/decision`, { method: 'POST', body: JSON.stringify(request) }),
  audit: (identity, workflowId) => call(identity, `/api/v1/audit/${encodeURIComponent(workflowId)}`),
}

export function useMocks(): boolean {
  return import.meta.env.VITE_USE_MOCKS === 'true'
}

export const api: CopilotApi = useMocks() ? mockApi : httpApi
