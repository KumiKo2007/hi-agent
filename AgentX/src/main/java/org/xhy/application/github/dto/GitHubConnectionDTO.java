package org.xhy.application.github.dto;

import java.util.List;

/** 公开响应只包含身份和平台授权范围。 */
public record GitHubConnectionDTO(String githubUserId, String githubLogin, List<String> repositories) {
    public GitHubConnectionDTO {
        repositories = List.copyOf(repositories);
    }
}
