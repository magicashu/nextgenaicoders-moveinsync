import type {
  ApprovalView,
  AuditResponse,
  EvidenceBundle,
  EvidenceItem,
  MetricResult,
  MorningBriefResponse,
  QuestionResponse,
  TrustPanel,
} from '../core/contracts'

// Frozen typed fixtures: every number is a hand-reconciled value from the dataset profile (G1 pinnacle-Slc
// as of 2026-06-08, G2 vanta-Aus as of 2026-08-01, healthy catalyst-Sac). Nothing here is a production claim.

export const RUN_ID = '00000000-0000-0000-0000-00000000a001'
export const ACTION_ID = '00000000-0000-0000-0000-00000000a002'
export const APPROVAL_ID = '00000000-0000-0000-0000-00000000a003'
const DATA_VERSION = 'data-8ed5b4eae158'
const T0 = '2026-06-08T08:00:00Z'

function metric(partial: Partial<MetricResult> & Pick<MetricResult, 'metricId' | 'metricName' | 'unit' | 'value'>): MetricResult {
  return {
    status: 'SUPPORTED',
    baselineValue: null,
    delta: null,
    numerator: null,
    denominator: null,
    supportingCount: 0,
    periodStart: '2026-06-01',
    periodEnd: '2026-06-07',
    filters: {},
    contractVersion: 'metrics-v1.1',
    dataVersion: DATA_VERSION,
    source: 'sql/metrics/' + partial.metricId.toLowerCase() + '.sql',
    caveats: [],
    ...partial,
  }
}

function item(partial: Partial<EvidenceItem> & Pick<EvidenceItem, 'evidenceId' | 'metricId' | 'value' | 'unit'>): EvidenceItem {
  return {
    baselineValue: null,
    delta: null,
    numerator: null,
    denominator: null,
    supportingCount: 0,
    periodStart: '2026-06-01',
    periodEnd: '2026-06-07',
    filters: {},
    source: 'sql/metrics/m01_delayed_trip_rate.sql',
    contractVersion: 'metrics-v1.1',
    dataVersion: DATA_VERSION,
    ...partial,
  }
}

export const g1M01 = metric({
  metricId: 'M01_DELAYED_TRIP_RATE', metricName: 'Delayed-trip rate', unit: 'PERCENT', value: 21.88, baselineValue: 12.28, delta: 9.6,
  numerator: 4357, denominator: 19913, supportingCount: 19913,
})

const headlineId = 'pinnacle-Slc:m01_delayed_trip_rate:2026-06-07'
const siteId = 'pinnacle-Slc:m01_delayed_trip_rate:site_id:2026-06-07:clearwater-campus'
const vendorId = 'pinnacle-Slc:m01_delayed_trip_rate:vendor_id:2026-06-07'

export const g1Evidence: EvidenceBundle = {
  items: [
    item({ evidenceId: headlineId, metricId: 'M01_DELAYED_TRIP_RATE', value: 21.88, unit: 'PERCENT', baselineValue: 12.28, delta: 9.6, numerator: 4357, denominator: 19913, supportingCount: 19913 }),
    item({ evidenceId: headlineId + ':impact', metricId: 'M01_DELAYED_TRIP_RATE', value: 1912, unit: 'COUNT', numerator: 3414, denominator: 7780, supportingCount: 19913, filters: { derivation: 'excess events and rider legs' }, source: 'anomaly-rules' }),
    item({ evidenceId: siteId, metricId: 'M01_DELAYED_TRIP_RATE', value: 24.07, unit: 'PERCENT', baselineValue: 12.86, delta: 11.21, numerator: 2226, denominator: 9247, supportingCount: 9247, filters: { site_id: 'Clearwater Campus', qualified: 'true' } }),
    item({ evidenceId: siteId + ':share', metricId: 'M01_DELAYED_TRIP_RATE', value: 51.1, unit: 'PERCENT', numerator: 2226, denominator: 4357, supportingCount: 9247, filters: { site_id: 'Clearwater Campus', measure: 'share of numerator' } }),
    item({ evidenceId: 'pinnacle-Slc:m01_delayed_trip_rate:shift_id:2026-06-07:10-30', metricId: 'M01_DELAYED_TRIP_RATE', value: 47.38, unit: 'PERCENT', baselineValue: 22.11, delta: 25.27, numerator: 425, denominator: 897, supportingCount: 897, filters: { shift_id: '10:30', qualified: 'true' } }),
    item({ evidenceId: 'pinnacle-Slc:m01_delayed_trip_rate:direction:2026-06-07:login', metricId: 'M01_DELAYED_TRIP_RATE', value: 23.91, unit: 'PERCENT', baselineValue: 10.98, delta: 12.93, numerator: 2444, denominator: 10223, supportingCount: 10223, filters: { direction: 'LOGIN', qualified: 'true' } }),
    item({ evidenceId: vendorId, metricId: 'M01_DELAYED_TRIP_RATE', value: 14, unit: 'COUNT', numerator: 14, denominator: 17, supportingCount: 4357, filters: { dimension: 'vendor_id', minimumVolume: '500' } }),
    item({ evidenceId: vendorId + ':pooja-mikhailov-travel', metricId: 'M01_DELAYED_TRIP_RATE', value: 28.37, unit: 'PERCENT', baselineValue: 15.67, delta: 12.7, numerator: 354, denominator: 1248, supportingCount: 1248, filters: { vendor_id: 'Pooja Mikhailov Travel', qualified: 'true' } }),
    item({ evidenceId: vendorId + ':rahul-mikhailov-travel', metricId: 'M01_DELAYED_TRIP_RATE', value: 17.15, unit: 'PERCENT', baselineValue: 9.89, delta: 7.26, numerator: 249, denominator: 1452, supportingCount: 1452, filters: { vendor_id: 'Rahul Mikhailov Travel', qualified: 'true' } }),
    item({ evidenceId: 'pinnacle-Slc:m03_delay_reason_mix:delay_reason:2026-06-07:driver', metricId: 'M03_DELAY_REASON_MIX', value: 39.3, unit: 'PERCENT', baselineValue: 33.4, delta: 5.9, numerator: 1711, denominator: 4357, supportingCount: 4357, filters: { delay_reason: 'DRIVER' }, source: 'sql/contributions/delay_reason_mix.sql' }),
    item({ evidenceId: 'pinnacle-Slc:m04_on_time_pickup_rate:2026-06-07', metricId: 'M04_ON_TIME_PICKUP_RATE', value: 74.23, unit: 'PERCENT', baselineValue: 78.71, delta: -4.48, numerator: 25729, denominator: 34661, supportingCount: 34661, source: 'sql/metrics/m04_on_time_pickup_rate.sql' }),
    item({ evidenceId: 'pinnacle-Slc:m09_median_cost_per_trip:2026-06-07', metricId: 'M09_MEDIAN_COST_PER_TRIP', value: 1020, unit: 'CURRENCY', baselineValue: 1144.74, delta: -124.74, denominator: 88308, supportingCount: 88308, source: 'sql/metrics/m09_median_cost_per_trip.sql' }),
    item({ evidenceId: 'pinnacle-Slc:m11_low_driver_rating_rate:2026-06-07', metricId: 'M11_LOW_DRIVER_RATING_RATE', value: 0.58, unit: 'PERCENT', baselineValue: 0.37, delta: 0.21, numerator: 147, denominator: 25475, supportingCount: 25475, source: 'sql/metrics/m11_low_driver_rating_rate.sql' }),
  ],
  confidence: 0.84,
  coverage: 19913,
  caveats: ['severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful', 'Data-quality note: EMPLOYEE_SIGN_OFF_TIME_VIOLATION alerts fell from about 3,788 per week to 0 in the week of 2026-05-18 while other alert types stayed stable. Classified as a data-regime change (alert configuration), not an operational issue.'],
}

export const g1Approval: ApprovalView = {
  approvalId: APPROVAL_ID,
  actionId: ACTION_ID,
  status: 'PENDING',
  actionType: 'CREATE_SITE_SHIFT_WATCHLIST',
  title: 'Place Clearwater Campus 09:00, 09:30 and 10:30 shifts on a one-week watchlist and open an investigation ticket',
  rationale: 'Every qualified vendor rose together, so this is a site and shift pattern rather than a single-vendor failure; watch and investigate before escalating.',
  scope: { businessUnit: 'pinnacle-Slc', metricId: 'M01_DELAYED_TRIP_RATE', windowEnd: '2026-06-07', site_id: 'Clearwater Campus', shift_id: '09:00,09:30,10:30', followUp: 'CREATE_INVESTIGATION_TICKET', watchDays: '7' },
  evidenceVersion: 'evidence-3f2a9c1b7d44',
  evidenceTimestamp: T0,
  createdAt: T0,
  expiresAt: '2026-06-08T08:30:00Z',
  consequence: 'Creates a mock watchlist entry for the listed site and shifts (7 days) and opens a mock investigation ticket. No message is sent to any vendor.',
}

export const g1Trust: TrustPanel = {
  runId: RUN_ID,
  traceId: 'trace-' + RUN_ID,
  finalStep: 'AWAITING_APPROVAL',
  dataVersion: DATA_VERSION,
  contractVersion: 'metrics-v1.1',
  workflowVersion: 'workflow-v1',
  promptVersion: 'prompts-v1',
  modelId: 'none',
  ruleVersion: 'anomaly-rules-v1',
  targetVersion: 'targets-v1',
  latencyMs: 1830,
  modelCalls: 0,
  fallbackCalls: 4,
  inputTokens: 0,
  outputTokens: 0,
  toolCalls: 7,
  confidence: 0.84,
  confidenceComponents: ['evidence items=13', 'failed branches=0', 'capability gaps=2', 'removed claims=0'],
  capabilityGaps: ['severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful', 'escort_present: Very few WOMAN_TRAVELLING_ALONE alerts (17)'],
  dataQualityNotes: ['EMPLOYEE_SIGN_OFF_TIME_VIOLATION step change classified as a data-regime change'],
  branchStatus: { vendor: 'COMPLETE', site_shift_direction: 'COMPLETE', delay_reason: 'COMPLETE', feedback: 'COMPLETE', cost_billing: 'COMPLETE', noshow_roster: 'COMPLETE' },
  transitions: [
    { node: 'INITIALIZE_RUN', subNode: null, outcome: 'initialized', durationMs: 1, startedAt: T0 },
    { node: 'AUTHORIZE_SCOPE', subNode: null, outcome: 'authorized', durationMs: 1, startedAt: T0 },
    { node: 'COMPUTE_METRIC_SNAPSHOT', subNode: null, outcome: 'snapshot', durationMs: 412, startedAt: T0 },
    { node: 'DETECT_ANOMALIES', subNode: null, outcome: 'issue', durationMs: 3, startedAt: T0 },
    { node: 'SUPERVISOR_PLAN', subNode: null, outcome: 'deterministic plan', durationMs: 2, startedAt: T0 },
    { node: 'RUN_INVESTIGATIONS', subNode: 'investigation.vendor.execute_analysis', outcome: 'ok', durationMs: 180, startedAt: T0 },
    { node: 'RUN_INVESTIGATIONS', subNode: 'investigation.site_shift_direction.execute_analysis', outcome: 'ok', durationMs: 240, startedAt: T0 },
    { node: 'RUN_INVESTIGATIONS', subNode: null, outcome: 'complete', durationMs: 910, startedAt: T0 },
    { node: 'EVIDENCE_CRITIC', subNode: null, outcome: 'pass', durationMs: 4, startedAt: T0 },
    { node: 'VERIFY_EVIDENCE', subNode: null, outcome: 'pass', durationMs: 3, startedAt: T0 },
    { node: 'COMPOSE_DECISION_BRIEF', subNode: null, outcome: 'composed', durationMs: 6, startedAt: T0 },
    { node: 'ACTION_POLICY_GATE', subNode: null, outcome: 'approval required', durationMs: 1, startedAt: T0 },
    { node: 'APPROVAL_INTERRUPT', subNode: null, outcome: 'paused', durationMs: 2, startedAt: T0 },
  ],
}

export const g1Brief: MorningBriefResponse = {
  runId: RUN_ID,
  workflowId: RUN_ID,
  businessUnit: 'pinnacle-Slc',
  asOfDate: '2026-06-08',
  persona: 'TRANSPORT_MANAGER',
  status: 'AWAITING_APPROVAL',
  operations: {
    headline: 'pinnacle-Slc: delayed-trip rate reached 21.88% in the week to 2026-06-07, up from 12.28% in the prior four weeks (configured target 10%)',
    status: 'AWAITING_APPROVAL',
    headlineKpi: { label: 'Delayed-trip rate', metric: g1M01, evidenceId: headlineId, comparison: 'prior four complete weeks', configuredTarget: '≤ 10%', targetLabel: 'Configured target, editable per tenant', meetsTarget: false },
    supportingKpis: [
      { label: 'On-time pickup rate', metric: metric({ metricId: 'M04_ON_TIME_PICKUP_RATE', metricName: 'On-time pickup rate', unit: 'PERCENT', value: 74.23, baselineValue: 78.71, delta: -4.48, numerator: 25729, denominator: 34661, supportingCount: 34661 }), evidenceId: 'pinnacle-Slc:m04_on_time_pickup_rate:2026-06-07', comparison: 'prior four complete weeks', configuredTarget: '≥ 90%', targetLabel: 'Configured target, editable per tenant', meetsTarget: false },
      { label: 'Median billed cost per trip', metric: metric({ metricId: 'M09_MEDIAN_COST_PER_TRIP', metricName: 'Median billed cost per trip', unit: 'CURRENCY', value: 1020, baselineValue: 1144.74, delta: -124.74, denominator: 88308, supportingCount: 88308 }), evidenceId: 'pinnacle-Slc:m09_median_cost_per_trip:2026-06-07', comparison: 'prior four complete weeks', configuredTarget: null, targetLabel: null, meetsTarget: false },
      { label: 'Low driver-rating rate', metric: metric({ metricId: 'M11_LOW_DRIVER_RATING_RATE', metricName: 'Low driver-rating rate', unit: 'PERCENT', value: 0.58, baselineValue: 0.37, delta: 0.21, numerator: 147, denominator: 25475, supportingCount: 25475 }), evidenceId: 'pinnacle-Slc:m11_low_driver_rating_rate:2026-06-07', comparison: 'prior four complete weeks', configuredTarget: null, targetLabel: null, meetsTarget: false },
    ],
    findings: [
      { claimId: 'c1', text: 'Delayed-trip rate reached 21.88% (4,357 of 19,913) in the week to 2026-06-07, up from 12.28% in the prior four complete weeks (+9.6 points).', kind: 'DIRECT', evidenceIds: [headlineId], worker: 'detector' },
      { claimId: 'c2', text: 'About 1,912 excess delayed trips affected 7,780 rider legs (about 3,414 more than the baseline rate implies).', kind: 'INFERRED', evidenceIds: [headlineId + ':impact'], worker: 'detector' },
      { claimId: 'c3', text: "Every vendor with at least 500 trips in both windows rose (14 vendors, range 17.2% to 28.4%); the change is not attributable to a single vendor.", kind: 'DIRECT', evidenceIds: [vendorId, vendorId + ':pooja-mikhailov-travel', vendorId + ':rahul-mikhailov-travel'], worker: 'vendor' },
      { claimId: 'c4', text: "Site 'Clearwater Campus' carries 51.1% of delayed trips at 24.07% (baseline 12.86%).", kind: 'DIRECT', evidenceIds: [siteId, siteId + ':share'], worker: 'site_shift_direction' },
      { claimId: 'c5', text: "Shift '10:30' carries 9.8% of delayed trips at 47.38% (baseline 22.11%).", kind: 'DIRECT', evidenceIds: ['pinnacle-Slc:m01_delayed_trip_rate:shift_id:2026-06-07:10-30'], worker: 'site_shift_direction' },
      { claimId: 'c6', text: "Direction 'LOGIN' carries 56.1% of delayed trips at 23.91% (baseline 10.98%).", kind: 'DIRECT', evidenceIds: ['pinnacle-Slc:m01_delayed_trip_rate:direction:2026-06-07:login'], worker: 'site_shift_direction' },
      { claimId: 'c7', text: 'Delay reason DRIVER rose to 39.3% of delayed trips from 33.4%.', kind: 'DIRECT', evidenceIds: ['pinnacle-Slc:m03_delay_reason_mix:delay_reason:2026-06-07:driver'], worker: 'delay_reason' },
      { claimId: 'c8', text: 'Median billed cost per trip did not rise (1020.00 versus 1144.74); no cost penalty is visible.', kind: 'DIRECT', evidenceIds: ['pinnacle-Slc:m09_median_cost_per_trip:2026-06-07'], worker: 'cost_billing' },
      { claimId: 'c9', text: 'Low driver-rating rate is flat at 0.58% (baseline 0.37%).', kind: 'DIRECT', evidenceIds: ['pinnacle-Slc:m11_low_driver_rating_rate:2026-06-07'], worker: 'feedback' },
      { claimId: 'c10', text: 'Leg-level on-time pickups fell from 78.71% to 74.23%, confirming the trip-level trend.', kind: 'DIRECT', evidenceIds: ['pinnacle-Slc:m04_on_time_pickup_rate:2026-06-07'], worker: 'noshow_roster' },
    ],
    caveats: [
      { claimId: 'c11', text: 'Misses configured target, editable per tenant of 10.00.', kind: 'CAVEAT', evidenceIds: [headlineId], worker: 'detector' },
      { claimId: 'c12', text: 'severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful', kind: 'CAVEAT', evidenceIds: [headlineId], worker: 'system' },
      { claimId: 'c13', text: 'Data-quality note: EMPLOYEE_SIGN_OFF_TIME_VIOLATION alerts fell from about 3,788 per week to 0 in the week of 2026-05-18 while other alert types stayed stable. Classified as a data-regime change (alert configuration), not an operational issue.', kind: 'CAVEAT', evidenceIds: [headlineId], worker: 'system' },
    ],
    recommendedAction: {
      actionId: ACTION_ID, runId: RUN_ID, type: 'CREATE_SITE_SHIFT_WATCHLIST', title: g1Approval.title, rationale: g1Approval.rationale, scope: g1Approval.scope,
      evidenceVersion: 'evidence-3f2a9c1b7d44', createdAt: T0, expiresAt: '2026-06-08T08:30:00Z', status: 'DRAFT_REQUIRES_APPROVAL',
    },
    approval: g1Approval,
    receipt: null,
  },
  leadership: {
    title: 'pinnacle-Slc — Delayed-trip rate summary as of 2026-06-08',
    narrative: [
      'pinnacle-Slc: delayed-trip rate reached 21.88% in the week to 2026-06-07, up from 12.28% in the prior four weeks (configured target 10%).',
      'About 1,900 excess delayed trips affected roughly 3,400 rider legs; confidence 0.84.',
      "Every vendor with at least 500 trips in both windows rose (14 vendors, range 17.2% to 28.4%); the change is not attributable to a single vendor.",
      "Site 'Clearwater Campus' carries 51.1% of delayed trips at 24.07% (baseline 12.86%).",
      'Median billed cost per trip did not rise (1020.00 versus 1144.74); no cost penalty is visible.',
      'Note: severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful',
      'Recommended: ' + g1Approval.title + ' (awaiting approval).',
    ],
    recommendation: g1Approval.title,
    forwardableText: '',
  },
  evidence: g1Evidence,
  trust: g1Trust,
  suggestedQuestions: [
    'Where is this anomaly concentrated by site and shift?',
    'Did every high-volume vendor deteriorate, or is one vendor driving the change?',
    'Which delay reasons changed among delayed trips?',
    'Did billed cost per trip move with the operational change?',
    'What evidence supports the recommended action, and how confident are we?',
  ],
  errors: [],
}
g1Brief.leadership.forwardableText = g1Brief.leadership.narrative.join('\n')

export const healthyBrief: MorningBriefResponse = {
  ...g1Brief,
  runId: '00000000-0000-0000-0000-00000000b001',
  workflowId: '00000000-0000-0000-0000-00000000b001',
  businessUnit: 'catalyst-Sac',
  status: 'HEALTHY',
  operations: {
    ...g1Brief.operations,
    headline: 'catalyst-Sac: no material operational anomaly as of 2026-06-08; delayed-trip rate is 4.33% versus 3.92% in the prior four weeks',
    status: 'HEALTHY',
    headlineKpi: { label: 'Delayed-trip rate', metric: metric({ metricId: 'M01_DELAYED_TRIP_RATE', metricName: 'Delayed-trip rate', unit: 'PERCENT', value: 4.33, baselineValue: 3.92, delta: 0.41, numerator: 210, denominator: 4849, supportingCount: 4849 }), evidenceId: 'catalyst-Sac:m01_delayed_trip_rate:2026-06-07', comparison: 'prior four complete weeks', configuredTarget: '≤ 10%', targetLabel: 'Configured target, editable per tenant', meetsTarget: true },
    supportingKpis: [],
    findings: [],
    caveats: [{ claimId: 'h1', text: 'Delayed-trip rate: healthy (Change of 0.41 points (10% relative) is within the materiality rule)', kind: 'CAVEAT', evidenceIds: ['catalyst-Sac:m01_delayed_trip_rate:2026-06-07'], worker: 'detector' }],
    approval: null,
    receipt: null,
  },
  leadership: { title: 'catalyst-Sac — Delayed-trip rate summary as of 2026-06-08', narrative: ['catalyst-Sac: no material operational anomaly as of 2026-06-08.', 'No intervention is proposed.'], recommendation: 'No action recommended', forwardableText: 'catalyst-Sac: no material operational anomaly as of 2026-06-08.\nNo intervention is proposed.' },
  evidence: { items: [item({ evidenceId: 'catalyst-Sac:m01_delayed_trip_rate:2026-06-07', metricId: 'M01_DELAYED_TRIP_RATE', value: 4.33, unit: 'PERCENT', baselineValue: 3.92, delta: 0.41, numerator: 210, denominator: 4849, supportingCount: 4849 })], confidence: 1, coverage: 4849, caveats: [] },
  trust: { ...g1Trust, runId: '00000000-0000-0000-0000-00000000b001', traceId: 'trace-healthy', finalStep: 'HEALTHY', latencyMs: 420, toolCalls: 0, fallbackCalls: 0, confidence: null, confidenceComponents: [], capabilityGaps: ['tracking_gap: No DEVICE_NOT_REACHABLE events for this tenant', 'escort_present: No WOMAN_TRAVELLING_ALONE events for this tenant'], dataQualityNotes: [], branchStatus: {}, transitions: g1Trust.transitions.slice(0, 4) },
}

export const g2Brief: MorningBriefResponse = {
  ...g1Brief,
  runId: '00000000-0000-0000-0000-00000000c001',
  workflowId: '00000000-0000-0000-0000-00000000c001',
  businessUnit: 'vanta-Aus',
  asOfDate: '2026-08-01',
  status: 'AWAITING_APPROVAL',
  operations: {
    ...g1Brief.operations,
    headline: 'vanta-Aus: delayed-trip rate reached 7.19% in the week to 2026-07-31, up from 3.19% in the prior four weeks (configured target 10%)',
    headlineKpi: { label: 'Delayed-trip rate', metric: metric({ metricId: 'M01_DELAYED_TRIP_RATE', metricName: 'Delayed-trip rate', unit: 'PERCENT', value: 7.19, baselineValue: 3.19, delta: 4.0, numerator: 380, denominator: 5285, supportingCount: 5285, periodStart: '2026-07-25', periodEnd: '2026-07-31' }), evidenceId: 'vanta-Aus:m01_delayed_trip_rate:2026-07-31', comparison: 'prior four complete weeks', configuredTarget: '≤ 10%', targetLabel: 'Configured target, editable per tenant', meetsTarget: true },
    supportingKpis: [
      { label: 'On-time pickup rate', metric: metric({ metricId: 'M04_ON_TIME_PICKUP_RATE', metricName: 'On-time pickup rate', unit: 'PERCENT', value: 88.18, baselineValue: 95.91, delta: -7.73, numerator: 10400, denominator: 11794, supportingCount: 11794, periodStart: '2026-07-27', periodEnd: '2026-07-31' }), evidenceId: 'vanta-Aus:m04_on_time_pickup_rate:2026-07-31', comparison: 'May baseline', configuredTarget: '≥ 90%', targetLabel: 'Configured target, editable per tenant', meetsTarget: false },
      { label: 'Tracking-gap rate', metric: metric({ metricId: 'M16_TRACKING_GAP_RATE', metricName: 'Tracking-gap rate', unit: 'PER_1000_TRIPS', value: 39.05, baselineValue: 14.08, delta: 24.97, numerator: 921, denominator: 23584, supportingCount: 23584, periodStart: '2026-07-01', periodEnd: '2026-07-31' }), evidenceId: 'vanta-Aus:m16_tracking_gap_rate:2026-07-31', comparison: 'May', configuredTarget: null, targetLabel: null, meetsTarget: false },
      { label: 'Low driver-rating rate', metric: metric({ metricId: 'M11_LOW_DRIVER_RATING_RATE', metricName: 'Low driver-rating rate', unit: 'PERCENT', value: 4.2, baselineValue: 2.75, delta: 1.45, numerator: 37, denominator: 882, supportingCount: 882, periodStart: '2026-07-01', periodEnd: '2026-07-31', caveats: ['Low feedback coverage: 3.9% of trips rated'] }), evidenceId: 'vanta-Aus:m11_low_driver_rating_rate:2026-07-31', comparison: 'May', configuredTarget: null, targetLabel: null, meetsTarget: false },
      { label: 'Cost per billed km', metric: metric({ metricId: 'M10_COST_PER_BILLED_KM', metricName: 'Cost per billed km', unit: 'CURRENCY_PER_KM', value: null, status: 'UNSUPPORTED', caveats: ['Unsupported: 100.0% of billed lines have zero km (Q2); cost per km is not computable'] }), evidenceId: 'vanta-Aus:m10_cost_per_billed_km:2026-07-31', comparison: '', configuredTarget: null, targetLabel: null, meetsTarget: false },
    ],
    findings: [
      { claimId: 'g2c1', text: 'Delayed-trip rate reached 7.19% (380 of 5,285) in the week to 2026-07-31, up from 3.19% in the prior four complete weeks (+4 points).', kind: 'DIRECT', evidenceIds: ['vanta-Aus:m01_delayed_trip_rate:2026-07-31'], worker: 'detector' },
      { claimId: 'g2c2', text: 'Leg-level on-time pickups fell from 95.91% to 88.18%, confirming the trip-level trend.', kind: 'DIRECT', evidenceIds: ['vanta-Aus:m04_on_time_pickup_rate:2026-07-31'], worker: 'noshow_roster' },
      { claimId: 'g2c3', text: 'Device-unreachable alerts doubled: 39.05 versus 14.08 per 1,000 trips.', kind: 'DIRECT', evidenceIds: ['vanta-Aus:m16_tracking_gap_rate:2026-07-31'], worker: 'tracking_safety_alerts' },
      { claimId: 'g2c4', text: 'Low driver-rating rate moved from 2.75% to 4.2%.', kind: 'DIRECT', evidenceIds: ['vanta-Aus:m11_low_driver_rating_rate:2026-07-31'], worker: 'feedback' },
      { claimId: 'g2c5', text: '5 of 5 qualified vendors deteriorated; 0 did not.', kind: 'DIRECT', evidenceIds: ['vanta-Aus:m01_delayed_trip_rate:vendor_id:2026-07-31'], worker: 'vendor' },
    ],
    caveats: [
      { claimId: 'g2c6', text: 'Low feedback coverage: 3.9% of trips rated', kind: 'CAVEAT', evidenceIds: ['vanta-Aus:m11_low_driver_rating_rate:2026-07-31'], worker: 'system' },
      { claimId: 'g2c7', text: 'cost_per_km: 100.0% of billed lines have zero km (Q2); cost per km is not computable', kind: 'CAVEAT', evidenceIds: ['vanta-Aus:m01_delayed_trip_rate:2026-07-31'], worker: 'system' },
      { claimId: 'g2c8', text: 'site_shift_direction: Single office with volume; site contribution is not discriminating', kind: 'CAVEAT', evidenceIds: ['vanta-Aus:m01_delayed_trip_rate:2026-07-31'], worker: 'system' },
    ],
    recommendedAction: { ...g1Brief.operations.recommendedAction, actionId: '00000000-0000-0000-0000-00000000c002', runId: '00000000-0000-0000-0000-00000000c001', type: 'CREATE_INVESTIGATION_TICKET', title: 'Open an investigation ticket for the cross-domain deterioration at Cedar Ridge Office', rationale: 'Trend, punctuality, tracking gaps and ratings move together with moderate confidence; investigate before escalating any vendor.', scope: { businessUnit: 'vanta-Aus', metricId: 'M01_DELAYED_TRIP_RATE', windowEnd: '2026-07-31', site_id: 'Cedar Ridge Office' } },
    approval: { ...g1Approval, approvalId: '00000000-0000-0000-0000-00000000c003', actionId: '00000000-0000-0000-0000-00000000c002', actionType: 'CREATE_INVESTIGATION_TICKET', title: 'Open an investigation ticket for the cross-domain deterioration at Cedar Ridge Office', rationale: 'Trend, punctuality, tracking gaps and ratings move together with moderate confidence; investigate before escalating any vendor.', scope: { businessUnit: 'vanta-Aus', site_id: 'Cedar Ridge Office' }, evidenceVersion: 'evidence-9b1c2d3e4f50', evidenceTimestamp: '2026-08-01T08:00:00Z', createdAt: '2026-08-01T08:00:00Z', expiresAt: '2026-08-01T08:30:00Z', consequence: 'Opens a mock investigation ticket in the tracking system. No external communication.' },
  },
  leadership: { title: 'vanta-Aus — Delayed-trip rate summary as of 2026-08-01', narrative: ['vanta-Aus: delayed-trip rate reached 7.19% in the week to 2026-07-31, up from 3.19% in the prior four weeks (configured target 10%).', 'Leg-level on-time pickups fell from 95.91% to 88.18%, confirming the trip-level trend.', 'Device-unreachable alerts doubled: 39.05 versus 14.08 per 1,000 trips.', 'Note: Low feedback coverage: 3.9% of trips rated', 'Note: cost per km is not computable for this tenant (all billed km are zero).', 'Recommended: Open an investigation ticket for the cross-domain deterioration at Cedar Ridge Office (awaiting approval).'], recommendation: 'Open an investigation ticket for the cross-domain deterioration at Cedar Ridge Office', forwardableText: '' },
  evidence: {
    items: [
      item({ evidenceId: 'vanta-Aus:m01_delayed_trip_rate:2026-07-31', metricId: 'M01_DELAYED_TRIP_RATE', value: 7.19, unit: 'PERCENT', baselineValue: 3.19, delta: 4.0, numerator: 380, denominator: 5285, supportingCount: 5285, periodStart: '2026-07-25', periodEnd: '2026-07-31' }),
      item({ evidenceId: 'vanta-Aus:m04_on_time_pickup_rate:2026-07-31', metricId: 'M04_ON_TIME_PICKUP_RATE', value: 88.18, unit: 'PERCENT', baselineValue: 95.91, delta: -7.73, numerator: 10400, denominator: 11794, supportingCount: 11794, periodStart: '2026-07-27', periodEnd: '2026-07-31', source: 'sql/metrics/m04_on_time_pickup_rate.sql' }),
      item({ evidenceId: 'vanta-Aus:m16_tracking_gap_rate:2026-07-31', metricId: 'M16_TRACKING_GAP_RATE', value: 39.05, unit: 'PER_1000_TRIPS', baselineValue: 14.08, delta: 24.97, numerator: 921, denominator: 23584, supportingCount: 23584, periodStart: '2026-07-01', periodEnd: '2026-07-31', source: 'sql/metrics/m16_tracking_gap_rate.sql' }),
      item({ evidenceId: 'vanta-Aus:m11_low_driver_rating_rate:2026-07-31', metricId: 'M11_LOW_DRIVER_RATING_RATE', value: 4.2, unit: 'PERCENT', baselineValue: 2.75, delta: 1.45, numerator: 37, denominator: 882, supportingCount: 882, periodStart: '2026-07-01', periodEnd: '2026-07-31', source: 'sql/metrics/m11_low_driver_rating_rate.sql' }),
      item({ evidenceId: 'vanta-Aus:m01_delayed_trip_rate:vendor_id:2026-07-31', metricId: 'M01_DELAYED_TRIP_RATE', value: 5, unit: 'COUNT', numerator: 5, denominator: 5, supportingCount: 5285, filters: { dimension: 'vendor_id', minimumVolume: '500' } }),
    ],
    confidence: 0.58,
    coverage: 5285,
    caveats: ['Low feedback coverage: 3.9% of trips rated', 'cost_per_km: 100.0% of billed lines have zero km (Q2); cost per km is not computable', 'site_shift_direction: Single office with volume; site contribution is not discriminating'],
  },
  trust: { ...g1Trust, runId: '00000000-0000-0000-0000-00000000c001', traceId: 'trace-g2', confidence: 0.58, confidenceComponents: ['evidence items=5', 'failed branches=0', 'capability gaps=3', 'removed claims=0'], capabilityGaps: ['cost_per_km: 100.0% of billed lines have zero km (Q2)', 'feedback: Low feedback coverage: 3.9% of trips rated', 'site_shift_direction: Single office with volume'], dataQualityNotes: [], branchStatus: { vendor: 'COMPLETE', site_shift_direction: 'COMPLETE', delay_reason: 'COMPLETE', feedback: 'COMPLETE', cost_billing: 'PARTIAL', tracking_safety_alerts: 'COMPLETE', noshow_roster: 'COMPLETE' } },
}
g2Brief.leadership.forwardableText = g2Brief.leadership.narrative.join('\n')

export const g1Audit: AuditResponse = {
  runId: RUN_ID,
  businessUnit: 'pinnacle-Slc',
  traceId: 'trace-' + RUN_ID,
  count: 5,
  events: [
    { eventId: 'e1', runId: RUN_ID, businessUnit: 'pinnacle-Slc', eventType: 'BRIEF_CREATED', payload: { actor: 'scheduler', evidenceVersion: 'evidence-3f2a9c1b7d44', claims: '13', confidence: '0.84' }, occurredAt: '2026-06-08T08:00:01Z', traceId: 'trace-' + RUN_ID },
    { eventId: 'e2', runId: RUN_ID, businessUnit: 'pinnacle-Slc', eventType: 'ACTION_POLICY_APPROVAL_REQUIRED', payload: { actor: 'scheduler', actionId: ACTION_ID, reasons: 'Action type CREATE_SITE_SHIFT_WATCHLIST is allowlisted; Scope bound to tenant pinnacle-Slc' }, occurredAt: '2026-06-08T08:00:01Z', traceId: 'trace-' + RUN_ID },
    { eventId: 'e3', runId: RUN_ID, businessUnit: 'pinnacle-Slc', eventType: 'ACTION_AWAITING_APPROVAL', payload: { actor: 'scheduler', approvalId: APPROVAL_ID, actionId: ACTION_ID, actionType: 'CREATE_SITE_SHIFT_WATCHLIST', evidenceVersion: 'evidence-3f2a9c1b7d44', expiresAt: '2026-06-08T08:30:00Z' }, occurredAt: '2026-06-08T08:00:02Z', traceId: 'trace-' + RUN_ID },
    { eventId: 'e4', runId: RUN_ID, businessUnit: 'pinnacle-Slc', eventType: 'APPROVAL_APPROVE', payload: { actor: 'transport-manager-demo', approvalId: APPROVAL_ID, decidedBy: 'transport-manager-demo', comment: 'Approved for one week' }, occurredAt: '2026-06-08T08:04:11Z', traceId: 'trace-' + RUN_ID },
    { eventId: 'e5', runId: RUN_ID, businessUnit: 'pinnacle-Slc', eventType: 'ACTION_EXECUTED', payload: { actor: 'transport-manager-demo', actionId: ACTION_ID, idempotencyKey: RUN_ID + ':' + ACTION_ID, externalReference: 'WATCH-7f3a', message: 'Mock site shift watchlist created' }, occurredAt: '2026-06-08T08:04:12Z', traceId: 'trace-' + RUN_ID },
  ],
}

export const refusedQuestion: QuestionResponse = {
  runId: null,
  businessUnit: 'pinnacle-Slc',
  intent: 'REFUSED',
  workers: [],
  refused: true,
  refusalReason: 'Cross-tenant comparison is not available to this identity',
  answer: 'I can only answer governed analytical questions about pinnacle-Slc. Cross-tenant comparison is not available to this identity.',
  supportingFindings: [],
  caveats: [],
  evidence: null,
  trust: null,
  draftedAction: null,
  followUps: g1Brief.suggestedQuestions,
}

export function answeredQuestion(question: string): QuestionResponse {
  const lower = question.toLowerCase()
  const worker = lower.includes('vendor') ? 'vendor' : lower.includes('cost') ? 'cost_billing' : lower.includes('reason') ? 'delay_reason' : 'site_shift_direction'
  const findings = g1Brief.operations.findings.filter((f) => f.worker === worker)
  return {
    runId: RUN_ID,
    businessUnit: 'pinnacle-Slc',
    intent: worker.toUpperCase(),
    workers: [worker],
    refused: false,
    refusalReason: null,
    answer: findings[0]?.text ?? g1Brief.operations.headline,
    supportingFindings: findings,
    caveats: g1Brief.operations.caveats,
    evidence: g1Evidence,
    trust: g1Trust,
    draftedAction: g1Brief.operations.recommendedAction,
    followUps: g1Brief.suggestedQuestions.filter((q) => !q.toLowerCase().includes(worker.split('_')[0])).slice(0, 3),
  }
}
