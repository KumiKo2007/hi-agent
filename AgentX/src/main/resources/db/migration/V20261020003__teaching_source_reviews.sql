-- 人工核对不修改固定知识版本；内容指纹失配后旧记录不再有效。
CREATE TABLE IF NOT EXISTS teaching_source_reviews (
    id varchar(36) PRIMARY KEY,
    user_id varchar(36) NOT NULL,
    version_id varchar(36) NOT NULL,
    source_id varchar(36) NOT NULL,
    content_hash varchar(64) NOT NULL,
    note text NOT NULL,
    reviewed_at varchar(40) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, version_id, source_id, content_hash)
);
