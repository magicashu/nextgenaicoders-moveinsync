export { TENANTS } from './identity'
export const WORKFLOW_NODES = [
  'INITIALIZE_RUN', 'AUTHORIZE_SCOPE', 'PROFILE_DATASET', 'BUILD_CAPABILITY_MATRIX',
  'COMPUTE_METRIC_SNAPSHOT', 'DETECT_ANOMALIES', 'PRIORITIZE_ISSUE', 'SUPERVISOR_PLAN',
  'VALIDATE_PLAN', 'RUN_INVESTIGATIONS', 'MERGE_EVIDENCE', 'EVIDENCE_CRITIC',
  'VERIFY_EVIDENCE', 'COMPOSE_DECISION_BRIEF', 'ACTION_POLICY_GATE', 'APPROVAL_INTERRUPT',
  'REVALIDATE_AND_EXECUTE', 'APPEND_AUDIT_EVENT',
] as const
export type WorkflowNode = typeof WORKFLOW_NODES[number]
export const NODE_AGENT: Record<WorkflowNode, string> = {
  INITIALIZE_RUN: 'system', AUTHORIZE_SCOPE: 'system', PROFILE_DATASET: 'system',
  BUILD_CAPABILITY_MATRIX: 'system', COMPUTE_METRIC_SNAPSHOT: 'system',
  DETECT_ANOMALIES: 'system', PRIORITIZE_ISSUE: 'system',
  SUPERVISOR_PLAN: 'supervisor', VALIDATE_PLAN: 'supervisor',
  RUN_INVESTIGATIONS: 'investigator', MERGE_EVIDENCE: 'investigator',
  EVIDENCE_CRITIC: 'critic', VERIFY_EVIDENCE: 'critic',
  COMPOSE_DECISION_BRIEF: 'briefing', ACTION_POLICY_GATE: 'briefing', APPROVAL_INTERRUPT: 'briefing',
  REVALIDATE_AND_EXECUTE: 'system', APPEND_AUDIT_EVENT: 'system',
}
export const AGENT_COLORS: Record<string, string> = {
  system: '#3C68D0', supervisor: '#3FA535', investigator: '#27D22E', critic: '#FF9D00', briefing: '#C13D6D',
}
