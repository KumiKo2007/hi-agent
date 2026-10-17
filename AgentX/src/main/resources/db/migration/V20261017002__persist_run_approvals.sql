CREATE TABLE IF NOT EXISTS agent_run_approvals (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL UNIQUE,
    service_id VARCHAR(64) NOT NULL,
    action_name VARCHAR(255) NOT NULL,
    action_description TEXT,
    target_repository VARCHAR(255) NOT NULL,
    arguments_json TEXT NOT NULL,
    schema_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    decided_by VARCHAR(64),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_run_approvals_run_status
    ON agent_run_approvals (run_id, status);
