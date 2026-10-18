ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS agent_version_id VARCHAR(36);
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS agent_version_number VARCHAR(20);
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS model_id VARCHAR(36);
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS model_connection_type VARCHAR(32);

COMMENT ON COLUMN agent_runs.agent_version_id IS '运行创建时固定的已发布 Agent 版本 ID';
COMMENT ON COLUMN agent_runs.agent_version_number IS '运行创建时固定的 Agent 版本号';
COMMENT ON COLUMN agent_runs.model_id IS '运行创建时固定的模型连接 ID';
COMMENT ON COLUMN agent_runs.model_connection_type IS '运行创建时固定的模型连接协议类型';
