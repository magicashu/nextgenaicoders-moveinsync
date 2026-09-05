// TypeScript mirror of the backend API DTOs (WS4 ApiDtos / frozen shared records).
// Every analytical number arrives with an evidence reference; the browser never computes a metric.

export type MetricUnit = 'PERCENT' | 'MINUTES' | 'CURRENCY' | 'CURRENCY_PER_KM' | 'PER_1000_TRIPS' | 'RATING' | 'COUNT'
export type MetricStatus = 'SUPPORTED' | 'UNSUPPORTED'
export type ActionType = 'CREATE_SITE_SHIFT_WATCHLIST' | 'CREATE_INVESTIGATION_TICKET' | 'DRAFT_VENDOR_ESCALATION' | 'DRAFT_COMMUNICATION'
export type BriefStatus = 'AWAITING_APPROVAL' | 'HEALTHY' | 'REPORT_ONLY' | 'FAILED' | string
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EDITED' | 'EXPIRED'
export type ReceiptStatus = 'EXECUTED' | 'APPROVED_NOT_EXECUTED'

export type MetricResult = {
  metricId: string
  metricName: string
  unit: MetricUnit
  status: MetricStatus
  value: number | null
  baselineValue: number | null
  delta: number | null
  numerator: number | null
  denominator: number | null
  supportingCount: number
  periodStart: string
  periodEnd: string
  filters: Record<string, string>
  contractVersion: string
  dataVersion: string
  source: string
  caveats: string[]
}

export type EvidenceItem = {
  evidenceId: string
  metricId: string
  value: number
  unit: string
  baselineValue: number | null
  delta: number | null
  numerator: number | null
  denominator: number | null
  supportingCount: number
  periodStart: string
  periodEnd: string
  filters: Record<string, string>
  source: string
  contractVersion: string
  dataVersion: string
}

export type EvidenceBundle = {
  items: EvidenceItem[]
  confidence: number
  coverage: number
  caveats: string[]
}

export type ActionProposal = {
  actionId: string
  runId: string
  type: ActionType
  title: string
  rationale: string
  scope: Record<string, string>
  evidenceVersion: string
  createdAt: string
  expiresAt: string
  status: 'DRAFT_REQUIRES_APPROVAL'
}

export type ExecutionReceipt = {
  actionId: string
  runId: string
  idempotencyKey: string
  status: ReceiptStatus
  attemptedAt: string
  completedAt: string | null
  externalReference: string | null
  message: string | null
}

export type DecisionBrief = {
  runId: string
  businessUnit: string
  asOfDate: string
  headline: string
  metric: MetricResult
  findings: string[]
  recommendedAction: ActionProposal
  evidence: EvidenceBundle
  status: BriefStatus
}

export type Kpi = {
  label: string
  metric: MetricResult
  evidenceId: string
  comparison: string
  configuredTarget: string | null
  targetLabel: string | null
  meetsTarget: boolean
}

export type Finding = {
  claimId: string
  text: string
  kind: 'DIRECT' | 'INFERRED' | 'CAVEAT' | 'RECOMMENDATION'
  evidenceIds: string[]
  worker: string
}

export type ApprovalView = {
  approvalId: string
  actionId: string
  status: ApprovalStatus
  actionType: ActionType
  title: string
  rationale: string
  scope: Record<string, string>
  evidenceVersion: string
  evidenceTimestamp: string
  createdAt: string
  expiresAt: string
  consequence: string
}

export type TransitionView = {
  node: string
  subNode: string | null
  outcome: string
  durationMs: number
  startedAt: string
}

export type TrustPanel = {
  runId: string
  traceId: string
  finalStep: string
  dataVersion: string
  contractVersion: string
  workflowVersion: string
  promptVersion: string
  modelId: string
  ruleVersion: string
  targetVersion: string
  latencyMs: number
  modelCalls: number
  fallbackCalls: number
  inputTokens: number
  outputTokens: number
  toolCalls: number
  confidence: number | null
  confidenceComponents: string[]
  capabilityGaps: string[]
  dataQualityNotes: string[]
  branchStatus: Record<string, string>
  transitions: TransitionView[]
}

export type OperationsSection = {
  headline: string
  status: BriefStatus
  headlineKpi: Kpi
  supportingKpis: Kpi[]
  findings: Finding[]
  caveats: Finding[]
  recommendedAction: ActionProposal
  approval: ApprovalView | null
  receipt: ExecutionReceipt | null
}

export type LeadershipSection = {
  title: string
  narrative: string[]
  recommendation: string
  forwardableText: string
}

export type MorningBriefResponse = {
  runId: string
  workflowId: string
  businessUnit: string
  asOfDate: string
  persona: string
  status: BriefStatus
  operations: OperationsSection
  leadership: LeadershipSection
  evidence: EvidenceBundle
  trust: TrustPanel
  suggestedQuestions: string[]
  errors: string[]
}

export type QuestionRequest = {
  question: string
  asOfDate?: string
  relatedRunId?: string
  persona?: string
}

export type QuestionResponse = {
  runId: string | null
  businessUnit: string
  intent: string
  workers: string[]
  refused: boolean
  refusalReason: string | null
  answer: string
  supportingFindings: Finding[]
  caveats: Finding[]
  evidence: EvidenceBundle | null
  trust: TrustPanel | null
  draftedAction: ActionProposal | null
  followUps: string[]
}

export type ApprovalDecisionRequest = {
  decision: 'APPROVE' | 'REJECT' | 'EDIT'
  comment?: string
  editedScope?: Record<string, string>
}

export type ApprovalDecisionResponse = {
  approvalId: string
  runId: string
  decision: string
  approvalStatus: ApprovalStatus
  workflowStatus: string
  receipt: ExecutionReceipt | null
  revalidation: string[]
  trust: TrustPanel
}

export type AuditEvent = {
  eventId: string
  runId: string
  businessUnit: string
  eventType: string
  payload: Record<string, string>
  occurredAt: string
  traceId: string
}

export type AuditResponse = {
  runId: string
  businessUnit: string
  traceId: string
  events: AuditEvent[]
  count: number
}

export type ApiError = {
  code: string
  message: string
  traceId: string
  occurredAt: string
  details: string[]
}

/** Trusted identity the demo shell sends as headers; a gateway injects these in production. */
export type Identity = {
  actorId: string
  businessUnit: string
  roles: string[]
}
