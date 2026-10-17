-- Runs keep the exact repository selected and authorized at submission time.
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS target_repository VARCHAR(200);

COMMENT ON COLUMN agent_runs.target_repository IS '运行创建时固定并授权的目标 GitHub 仓库';
