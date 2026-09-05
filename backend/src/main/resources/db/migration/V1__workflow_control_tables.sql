CREATE TABLE workflow_checkpoint (
    run_id UUID PRIMARY KEY,
    business_unit VARCHAR(120) NOT NULL,
    workflow_step VARCHAR(80) NOT NULL,
    state_json JSONB NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approval_decision (
    approval_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES workflow_checkpoint(run_id),
    action_id UUID NOT NULL,
    decision VARCHAR(40) NOT NULL,
    decided_by VARCHAR(160) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    comment TEXT
);

CREATE TABLE action_execution (
    action_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES workflow_checkpoint(run_id),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    revalidated_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ
);

CREATE TABLE audit_event (
    sequence_id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    run_id UUID NOT NULL,
    business_unit VARCHAR(120) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_event_run_sequence_idx ON audit_event (run_id, sequence_id);
