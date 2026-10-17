package org.xhy.application.github.service;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.xhy.application.run.service.RunTargetRepositoryAuthorization;
import org.xhy.infrastructure.exception.BusinessException;

/** Adapts run target authorization to the user's saved GitHub repository connection. */
@Component
public class GitHubRunTargetRepositoryAuthorization implements RunTargetRepositoryAuthorization {
    private final GitHubConnectionAppService connections;

    public GitHubRunTargetRepositoryAuthorization(GitHubConnectionAppService connections) {
        this.connections = connections;
    }

    @Override
    public String authorize(String userId, String requestedRepository) {
        String repository = normalize(requestedRepository);
        connections.verifyAuthorizedRepository(userId, repository);
        return repository;
    }

    @Override
    public void revalidate(String userId, String authorizedRepository) {
        connections.verifyAuthorizedRepository(userId, normalize(authorizedRepository));
    }

    private String normalize(String repository) {
        if (repository == null) {
            throw new BusinessException("请选择要授权的目标仓库");
        }
        String normalized = repository.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{1,39}/[a-z0-9_.-]{1,100}")) {
            throw new BusinessException("仓库名称应为 owner/name");
        }
        return normalized;
    }
}
