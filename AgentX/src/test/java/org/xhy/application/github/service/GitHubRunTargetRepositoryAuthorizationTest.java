package org.xhy.application.github.service;

import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubRunTargetRepositoryAuthorizationTest {
    @Test
    void authorizesCanonicalRepositoryAndRevalidatesItThroughTheIntegration() {
        List<String> checks = new ArrayList<>();
        GitHubConnectionAppService connections = new GitHubConnectionAppService(null, null, null) {
            @Override
            public void verifyAuthorizedRepository(String userId, String repositoryName) {
                checks.add(userId + ":" + repositoryName);
            }
        };
        GitHubRunTargetRepositoryAuthorization authorization = new GitHubRunTargetRepositoryAuthorization(connections);

        String canonical = authorization.authorize("user-1", "KumiKo2007/Hi-Agent");
        authorization.revalidate("user-1", canonical);

        assertEquals("kumiko2007/hi-agent", canonical);
        assertEquals(List.of("user-1:kumiko2007/hi-agent", "user-1:kumiko2007/hi-agent"), checks);
    }

    @Test
    void rejectsMalformedTargetBeforeCallingTheIntegration() {
        GitHubConnectionAppService connections = new GitHubConnectionAppService(null, null, null) {
            @Override
            public void verifyAuthorizedRepository(String userId, String repositoryName) {
                throw new AssertionError("malformed target must not reach GitHub authorization");
            }
        };
        GitHubRunTargetRepositoryAuthorization authorization = new GitHubRunTargetRepositoryAuthorization(connections);

        assertThrows(BusinessException.class, () -> authorization.authorize("user-1", "https://github.com/o/r"));
    }
}
