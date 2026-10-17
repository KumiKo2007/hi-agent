package org.xhy.infrastructure.repository.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGitHubConnectionRepository implements GitHubConnectionRepository {
    private static final TypeReference<List<String>> REPOSITORY_LIST = new TypeReference<>() {
    };
    private final GitHubConnectionMapper mapper;
    private final ObjectMapper json;

    public MyBatisGitHubConnectionRepository(GitHubConnectionMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public Optional<GitHubConnection> findByOwner(String ownerId) {
        return Optional.ofNullable(mapper.findByUserId(ownerId)).map(this::toDomain);
    }

    @Override
    public Optional<GitHubConnection> findByOwnerForUpdate(String ownerId) {
        return Optional.ofNullable(mapper.findByUserIdForUpdate(ownerId)).map(this::toDomain);
    }

    @Override
    public void save(GitHubConnection connection) {
        GitHubConnectionRow row = new GitHubConnectionRow();
        row.setUserId(connection.ownerId());
        row.setGithubUserId(connection.githubUserId());
        row.setGithubLogin(connection.githubLogin());
        row.setEncryptedToken(connection.encryptedToken());
        try {
            row.setRepositoriesJson(json.writeValueAsString(connection.repositories()));
        } catch (JsonProcessingException e) {
            throw new BusinessException("GitHub 仓库范围保存失败");
        }
        mapper.upsert(row);
    }

    @Override
    public void deleteByOwner(String ownerId) {
        mapper.deleteByUserId(ownerId);
    }

    private GitHubConnection toDomain(GitHubConnectionRow row) {
        try {
            List<String> repositories = json.readValue(row.getRepositoriesJson(), REPOSITORY_LIST);
            return new GitHubConnection(row.getUserId(), row.getGithubUserId(), row.getGithubLogin(),
                    row.getEncryptedToken(), repositories);
        } catch (JsonProcessingException e) {
            throw new BusinessException("GitHub 连接数据无效，请重新连接");
        }
    }
}
