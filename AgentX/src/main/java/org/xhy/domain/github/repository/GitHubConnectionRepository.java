package org.xhy.domain.github.repository;

import org.xhy.domain.github.model.GitHubConnection;

import java.util.Optional;

/** 服务端 GitHub 连接持久化边界；所有操作都以当前使用者标识限定。 */
public interface GitHubConnectionRepository {
    Optional<GitHubConnection> findByOwner(String ownerId);

    Optional<GitHubConnection> findByOwnerForUpdate(String ownerId);

    void save(GitHubConnection connection);

    void deleteByOwner(String ownerId);
}
