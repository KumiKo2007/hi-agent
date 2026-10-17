ALTER TABLE agent_run_approvals
    ADD COLUMN IF NOT EXISTS reconciliation_evidence_reference VARCHAR(512);

ALTER TABLE agent_run_reconciliations
    ADD COLUMN IF NOT EXISTS evidence_reference VARCHAR(512);
