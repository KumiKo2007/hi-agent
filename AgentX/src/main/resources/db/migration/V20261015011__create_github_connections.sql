-- 使用者自己的 GitHub 凭据只以加密形式存储；仓库名单是平台侧进一步收紧的授权范围。
CREATE TABLE github_connections (
    user_id VARCHAR(64) PRIMARY KEY,
    github_user_id VARCHAR(64) NOT NULL,
    github_login VARCHAR(255) NOT NULL,
    encrypted_token TEXT NOT NULL,
    repositories_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
