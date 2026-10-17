ALTER TABLE agent_run_approvals
    ADD COLUMN IF NOT EXISTS reconciliation_state VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS reconciliation_outcome VARCHAR(32),
    ADD COLUMN IF NOT EXISTS reconciliation_reason TEXT,
    ADD COLUMN IF NOT EXISTS reconciliation_evidence TEXT,
    ADD COLUMN IF NOT EXISTS reconciliation_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reconciliation_at TIMESTAMP;

UPDATE agent_run_approvals
SET reconciliation_state = 'NEEDS_CHECK'
WHERE status = 'RECONCILIATION_REQUIRED' AND reconciliation_state = 'NONE';

CREATE TABLE IF NOT EXISTS agent_run_reconciliations (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    approval_id VARCHAR(36) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64),
    evidence TEXT NOT NULL,
    external_resource_id TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_run_reconciliations_approval_created
    ON agent_run_reconciliations (approval_id, created_at DESC);
