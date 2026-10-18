ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS lease_token VARCHAR(36);
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_agent_runs_recovery
    ON agent_runs (status, lease_until, created_at)
    WHERE deleted_at IS NULL;
