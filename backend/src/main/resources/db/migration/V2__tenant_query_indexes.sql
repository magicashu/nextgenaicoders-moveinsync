-- Scoped history and resume lookups. These do not replace authorization predicates.
CREATE INDEX workflow_checkpoint_tenant_updated_idx
    ON workflow_checkpoint (business_unit, updated_at DESC, run_id);
CREATE INDEX audit_event_tenant_run_sequence_idx
    ON audit_event (business_unit, run_id, sequence_id);
CREATE INDEX approval_decision_run_time_idx
    ON approval_decision (run_id, decided_at DESC);
CREATE INDEX action_execution_run_status_idx
    ON action_execution (run_id, status);
