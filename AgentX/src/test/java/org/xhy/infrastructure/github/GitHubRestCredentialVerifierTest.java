package org.xhy.infrastructure.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** GitHub 只读核验边界，使用确定性的 GitHub 响应，不访问网络。 */
class GitHubRestCredentialVerifierTest {
    @Test
    void should_only_acceptRepositoriesReturnedForTheAuthenticatedCredential() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        GitHubApiTransport transport = (token, path) -> {
            assertEquals("github_pat_test_only_token_12345", token);
            calls.incrementAndGet();
            try {
                if (path.equals("/user")) {
                    return json.readTree("{\"id\":101,\"login\":\"alice\"}");
                }
                if (path.equals("/user/repos?per_page=100&page=1")) {
                    return json.readTree("[{\"full_name\":\"Alice/One\",\"has_issues\":true,"
                            + "\"archived\":false,\"disabled\":false}]");
                }
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            throw new AssertionError("意外的 GitHub API 请求");
        };

        GitHubRestCredentialVerifier verifier = new GitHubRestCredentialVerifier(transport);
        assertEquals("alice", verifier.verify("github_pat_test_only_token_12345", List.of("alice/one")).login());
        assertThrows(BusinessException.class,
                () -> verifier.verify("github_pat_test_only_token_12345", List.of("alice/two")));
        assertEquals(4, calls.get());
    }
}
