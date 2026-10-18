package org.xhy.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 通过固定的 GitHub API 主机核验凭据身份及可访问的仓库，不执行写操作。 */
@Component
public class GitHubRestCredentialVerifier implements GitHubCredentialVerifier {
    private static final int MAX_PAGES = 20;
    private final GitHubApiTransport transport;

    public GitHubRestCredentialVerifier(GitHubApiTransport transport) {
        this.transport = transport;
    }

    @Override
    public Identity verify(String token, List<String> repositories) {
        JsonNode user = transport.get(token, "/user");
        String id = user.path("id").asText();
        String login = user.path("login").asText();
        if (id.isBlank() || login.isBlank()) {
            throw new BusinessException("无法核验 GitHub 身份");
        }

        Set<String> pending = new HashSet<>(repositories);
        for (int page = 1; page <= MAX_PAGES && !pending.isEmpty(); page++) {
            JsonNode pageResult = transport.get(token, "/user/repos?per_page=100&page=" + page);
            if (!pageResult.isArray()) {
                throw new BusinessException("无法核验 GitHub 仓库范围");
            }
            for (JsonNode repository : pageResult) {
                String fullName = repository.path("full_name").asText().toLowerCase(Locale.ROOT);
                if (pending.contains(fullName) && repository.path("has_issues").asBoolean(false)
                        && !repository.path("archived").asBoolean(false)
                        && !repository.path("disabled").asBoolean(false)) {
                    pending.remove(fullName);
                }
            }
            if (pageResult.size() < 100) {
                break;
            }
        }
        if (!pending.isEmpty()) {
            throw new BusinessException("选定仓库不在该凭据可访问范围内，或未启用 Issues");
        }
        return new Identity(id, login);
    }

}
