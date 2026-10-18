package org.xhy.domain.github.model;

import java.util.List;

/** 使用者自己的 GitHub 写入身份及平台授权的仓库范围。密文只允许服务端持有。 */
public record GitHubConnection(String ownerId, String githubUserId, String githubLogin, String encryptedToken,
        List<String> repositories) {

    public GitHubConnection {
        repositories = List.copyOf(repositories);
    }

    public GitHubConnection withRepositories(List<String> narrowedRepositories) {
        return new GitHubConnection(ownerId, githubUserId, githubLogin, encryptedToken, narrowedRepositories);
    }
}
