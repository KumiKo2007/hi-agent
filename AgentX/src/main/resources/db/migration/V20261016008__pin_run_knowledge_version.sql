-- A run keeps the exact ready knowledge snapshot selected at creation time.
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS knowledge_version_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_agent_runs_knowledge_version
    ON agent_runs (knowledge_version_id);

COMMENT ON COLUMN agent_runs.knowledge_version_id IS '运行创建时固定的可检索知识版本 ID';
