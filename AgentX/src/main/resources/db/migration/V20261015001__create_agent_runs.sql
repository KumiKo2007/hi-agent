CREATE TABLE IF NOT EXISTS agent_runs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_runs_owner_session_time
    ON agent_runs (user_id, session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_run_steps (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    sequence_no INTEGER NOT NULL,
    kind VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    UNIQUE (run_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_agent_run_steps_run_sequence
    ON agent_run_steps (run_id, sequence_no);
