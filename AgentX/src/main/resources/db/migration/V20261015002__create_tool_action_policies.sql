CREATE TABLE IF NOT EXISTS tool_action_policies (
    service_id VARCHAR(36) NOT NULL,
    action_name VARCHAR(255) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    schema_json JSONB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (service_id, action_name),
    CONSTRAINT fk_tool_action_service FOREIGN KEY (service_id) REFERENCES tools(id) ON DELETE CASCADE,
    CONSTRAINT chk_tool_action_classification CHECK (classification IN ('READ_ONLY', 'WRITE_REQUIRES_APPROVAL'))
);

CREATE TABLE IF NOT EXISTS agent_tool_actions (
    agent_id VARCHAR(36) NOT NULL,
    service_id VARCHAR(36) NOT NULL,
    action_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (agent_id, service_id, action_name),
    CONSTRAINT fk_agent_tool_action_agent FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_action_service FOREIGN KEY (service_id) REFERENCES tools(id) ON DELETE CASCADE
);
