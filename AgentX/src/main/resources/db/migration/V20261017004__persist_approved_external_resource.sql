ALTER TABLE agent_run_approvals
    ADD COLUMN IF NOT EXISTS external_resource_id TEXT;
