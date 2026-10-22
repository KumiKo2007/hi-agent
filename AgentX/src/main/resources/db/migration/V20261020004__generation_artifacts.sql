-- 内容目标/运行与不可变产物修订。旧 agent_runs 无需迁移或回填。
CREATE TABLE IF NOT EXISTS generation_artifacts (
    id varchar(64) PRIMARY KEY, user_id varchar(64) NOT NULL, kind varchar(64) NOT NULL,
    status varchar(32) NOT NULL, current_run_id varchar(64) NOT NULL,
    current_revision_id varchar(64), confirmed_revision_id varchar(64), artifact_json jsonb NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(), updated_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_generation_artifacts_owner ON generation_artifacts(user_id,kind,created_at);
CREATE TABLE IF NOT EXISTS generation_runs (
    id varchar(64) PRIMARY KEY, artifact_id varchar(64) NOT NULL, user_id varchar(64) NOT NULL,
    status varchar(32) NOT NULL CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    error text, output_revision_id varchar(64), run_json jsonb NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(), updated_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_generation_runs_artifact ON generation_runs(artifact_id,user_id);
CREATE TABLE IF NOT EXISTS artifact_revisions (
    id varchar(64) PRIMARY KEY, artifact_id varchar(64) NOT NULL, user_id varchar(64) NOT NULL,
    run_id varchar(64) NOT NULL, parent_revision_id varchar(64), input_revision_id varchar(64),
    knowledge_version_id varchar(64), revision_json jsonb NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(), updated_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_artifact_revisions_owner ON artifact_revisions(artifact_id,user_id,created_at);
