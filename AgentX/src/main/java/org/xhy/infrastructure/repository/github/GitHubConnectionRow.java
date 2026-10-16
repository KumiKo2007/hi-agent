package org.xhy.infrastructure.repository.github;

/** 数据库行结构；encryptedToken 永远不能直接作为 API 响应。 */
public class GitHubConnectionRow {
    private String userId;
    private String githubUserId;
    private String githubLogin;
    private String encryptedToken;
    private String repositoriesJson;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public void setGithubUserId(String githubUserId) {
        this.githubUserId = githubUserId;
    }

    public String getGithubLogin() {
        return githubLogin;
    }

    public void setGithubLogin(String githubLogin) {
        this.githubLogin = githubLogin;
    }

    public String getEncryptedToken() {
        return encryptedToken;
    }

    public void setEncryptedToken(String encryptedToken) {
        this.encryptedToken = encryptedToken;
    }

    public String getRepositoriesJson() {
        return repositoriesJson;
    }

    public void setRepositoriesJson(String repositoriesJson) {
        this.repositoriesJson = repositoriesJson;
    }
}
