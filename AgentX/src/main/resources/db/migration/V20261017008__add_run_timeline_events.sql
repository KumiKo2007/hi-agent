CREATE TABLE IF NOT EXISTS agent_run_timeline_events (
    id VARCHAR(36) PRIMARY KEY,
    event_order BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    step_id VARCHAR(36),
    event_type VARCHAR(48) NOT NULL,
    event_status VARCHAR(48) NOT NULL,
    safe_summary VARCHAR(512) NOT NULL,
    details_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_run_timeline_events_run_order
    ON agent_run_timeline_events (run_id, event_order)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE agent_run_timeline_events IS
    'Append-only allowlisted owner timeline metadata; excludes prompts, credentials, raw tool arguments and outputs';
