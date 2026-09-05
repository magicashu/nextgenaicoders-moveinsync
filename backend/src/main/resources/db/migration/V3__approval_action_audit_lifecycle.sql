-- Governance and actions lifecycle (WS3). Extends the V1 control tables without rewriting them.

CREATE TABLE approval_request (
    approval_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES workflow_checkpoint(run_id),
    business_unit VARCHAR(120) NOT NULL,
    action_id UUID NOT NULL,
    proposal_json JSONB NOT NULL,
    evidence_version VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EDITED', 'EXPIRED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);

-- at most one pending approval per action
CREATE UNIQUE INDEX approval_request_pending_action_idx ON approval_request (action_id) WHERE status = 'PENDING';
CREATE INDEX approval_request_business_unit_status_idx ON approval_request (business_unit, status);

-- decisions now reference the request and may carry an edited proposal
ALTER TABLE approval_decision ADD CONSTRAINT approval_decision_request_fk FOREIGN KEY (approval_id) REFERENCES approval_request(approval_id);
ALTER TABLE approval_decision ADD COLUMN edited_proposal_json JSONB;
ALTER TABLE approval_decision ADD CONSTRAINT approval_decision_type_chk CHECK (decision IN ('APPROVE', 'REJECT', 'EDIT'));
ALTER TABLE approval_decision ADD CONSTRAINT approval_decision_edit_chk CHECK (decision <> 'EDIT' OR edited_proposal_json IS NOT NULL);

-- idempotent execution receipts
ALTER TABLE action_execution ADD COLUMN business_unit VARCHAR(120);
ALTER TABLE action_execution ADD COLUMN action_type VARCHAR(60);
ALTER TABLE action_execution ADD COLUMN evidence_version VARCHAR(120);
ALTER TABLE action_execution ADD COLUMN claimed_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE action_execution ADD COLUMN external_reference VARCHAR(200);
ALTER TABLE action_execution ADD COLUMN message TEXT;
ALTER TABLE action_execution ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE action_execution ADD CONSTRAINT action_execution_status_chk CHECK (status IN ('EXECUTING', 'EXECUTED', 'APPROVED_NOT_EXECUTED'));

-- tamper-evident, append-only audit ledger
ALTER TABLE audit_event ADD COLUMN trace_id VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE audit_event ADD COLUMN previous_hash CHAR(64) NOT NULL DEFAULT repeat('0', 64);
ALTER TABLE audit_event ADD COLUMN event_hash CHAR(64) NOT NULL DEFAULT '';
CREATE INDEX audit_event_business_unit_idx ON audit_event (business_unit, sequence_id DESC);

CREATE OR REPLACE FUNCTION audit_event_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is not permitted', TG_OP USING ERRCODE = '2F004';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_update BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();

CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_immutable();
