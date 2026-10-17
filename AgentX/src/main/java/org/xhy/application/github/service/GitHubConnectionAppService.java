package org.xhy.application.github.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xhy.application.github.dto.GitHubConnectionDTO;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** 使用者 GitHub 连接管理；模型和公开接口均不能读取凭据。 */
@Service
public class GitHubConnectionAppService {
    private final GitHubConnectionRepository repository;
    private final GitHubCredentialVerifier verifier;
    private final GitHubTokenCipher cipher;

    public GitHubConnectionAppService(GitHubConnectionRepository repository, GitHubCredentialVerifier verifier,
            GitHubTokenCipher cipher) {
        this.repository = repository;
        this.verifier = verifier;
        this.cipher = cipher;
    }

    /** 用使用者提供的凭据建立连接，并限制为选定仓库。 */
    @Transactional
    public GitHubConnectionDTO connect(String ownerId, String token, List<String> repositories) {
        requireOwner(ownerId);
        if (token == null || !token.matches("github_pat_[A-Za-z0-9_]{20,512}")) {
            throw new BusinessException("请提供有效的 GitHub 凭据");
        }
        List<String> allowed = validateRepositories(repositories);
        String encrypted = cipher.encrypt(ownerId, token);
        GitHubCredentialVerifier.Identity identity = verifier.verify(token, allowed);
        GitHubConnection connection = new GitHubConnection(ownerId, identity.id(), identity.login(), encrypted,
                allowed);
        repository.save(connection);
        return toDTO(connection);
    }

    /** 查询当前使用者连接；不存在时返回 null。 */
    public GitHubConnectionDTO get(String ownerId) {
        requireOwner(ownerId);
        return repository.findByOwner(ownerId).map(this::toDTO).orElse(null);
    }

    /** 只能把已有仓库集合缩小；要添加仓库须重新提交并核验凭据。 */
    @Transactional
    public GitHubConnectionDTO narrowRepositories(String ownerId, List<String> repositories) {
        requireOwner(ownerId);
        List<String> narrowed = validateRepositories(repositories);
        GitHubConnection existing = repository.findByOwnerForUpdate(ownerId)
                .orElseThrow(() -> new BusinessException("GitHub 连接不存在"));
        if (!existing.repositories().containsAll(narrowed)) {
            throw new BusinessException("只能缩小已授权仓库范围；添加仓库请重新连接");
        }
        GitHubConnection updated = existing.withRepositories(narrowed);
        repository.save(updated);
        return toDTO(updated);
    }

    /** 本地撤销连接；后续获取凭据的操作立即失去授权。 */
    @Transactional
    public void revoke(String ownerId) {
        requireOwner(ownerId);
        repository.deleteByOwner(ownerId);
    }

    /** 仅供受信任的服务端工具连接适配层调用，执行前重读撤权和仓库范围。 */
    public <T> T withAuthorizedToken(String ownerId, String repositoryName, Function<String, T> action) {
        requireOwner(ownerId);
        String normalized = normalizeRepository(repositoryName);
        GitHubConnection connection = repository.findByOwner(ownerId)
                .orElseThrow(() -> new BusinessException("GitHub 连接未授权或已撤销"));
        if (!connection.repositories().contains(normalized)) {
            throw new BusinessException("目标仓库不在 GitHub 连接授权范围内");
        }
        return action.apply(cipher.decrypt(ownerId, connection.encryptedToken()));
    }

    /** Revalidates the saved GitHub credential and exact repository immediately before a write. */
    public void verifyAuthorizedRepository(String ownerId, String repositoryName) {
        requireOwner(ownerId);
        String normalized = normalizeRepository(repositoryName);
        GitHubConnection connection = repository.findByOwner(ownerId)
                .orElseThrow(() -> new BusinessException("GitHub 连接未授权或已撤销"));
        if (!connection.repositories().contains(normalized)) {
            throw new BusinessException("目标仓库不在 GitHub 连接授权范围内");
        }
        String token = cipher.decrypt(ownerId, connection.encryptedToken());
        GitHubCredentialVerifier.Identity current = verifier.verify(token, List.of(normalized));
        if (!connection.githubUserId().equals(current.id())) {
            throw new BusinessException("GitHub 身份已变化，请重新连接");
        }
    }

    private GitHubConnectionDTO toDTO(GitHubConnection connection) {
        return new GitHubConnectionDTO(connection.githubUserId(), connection.githubLogin(), connection.repositories());
    }

    private List<String> validateRepositories(List<String> repositories) {
        if (repositories == null || repositories.isEmpty() || repositories.size() > 100) {
            throw new BusinessException("请选择 1 到 100 个 GitHub 仓库");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String repository : repositories) {
            unique.add(normalizeRepository(repository));
        }
        if (unique.size() != repositories.size()) {
            throw new BusinessException("仓库列表不能重复");
        }
        return List.copyOf(unique);
    }

    private String normalizeRepository(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}")) {
            throw new BusinessException("GitHub 仓库名称应为 owner/name");
        }
        return repository.toLowerCase(Locale.ROOT);
    }

    private void requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new BusinessException("请先登录");
        }
    }
}
