ALTER TABLE agent_runs
    ADD COLUMN IF NOT EXISTS approval_policy VARCHAR(48) NOT NULL DEFAULT 'CREATOR_DEFAULT';

ALTER TABLE agent_run_approvals
    ADD COLUMN IF NOT EXISTS approval_source VARCHAR(40) NOT NULL DEFAULT 'USER';

CREATE TABLE IF NOT EXISTS agent_tool_action_preauthorizations (
    agent_id VARCHAR(36) NOT NULL,
    service_id VARCHAR(36) NOT NULL,
    action_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (agent_id, service_id, action_name),
    CONSTRAINT fk_agent_action_preauth_selection
        FOREIGN KEY (agent_id, service_id, action_name)
        REFERENCES agent_tool_actions (agent_id, service_id, action_name) ON DELETE CASCADE
);
