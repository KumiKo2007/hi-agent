package org.xhy.interfaces.api.portal.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.github.service.GitHubConnectionAppService;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubTokenCipher;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GitHub 连接公开 HTTP 接口的行为测试，不调用 GitHub，也不创建 Issue。 */
class GitHubConnectionControllerTest {

    private static final String PATH = "/users/github-connection";
    private static final String TOKEN = "github_pat_test_only_token_12345";

    private final Map<String, GitHubConnection> connections = new HashMap<>();
    private final GitHubConnectionRepository repository = new GitHubConnectionRepository() {
        @Override
        public Optional<GitHubConnection> findByOwner(String ownerId) {
            return Optional.ofNullable(connections.get(ownerId));
        }

        @Override
        public Optional<GitHubConnection> findByOwnerForUpdate(String ownerId) {
            return findByOwner(ownerId);
        }

        @Override
        public void save(GitHubConnection connection) {
            connections.put(connection.ownerId(), connection);
        }

        @Override
        public void deleteByOwner(String ownerId) {
            connections.remove(ownerId);
        }
    };
    private final GitHubCredentialVerifier verifier = (token, repositories) -> {
        if (!TOKEN.equals(token) || !List.of("alice/one", "alice/two").containsAll(repositories)) {
            throw new IllegalArgumentException("无效授权");
        }
        return new GitHubCredentialVerifier.Identity("101", "alice");
    };
    private final GitHubTokenCipher cipher = new GitHubTokenCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    private final GitHubConnectionAppService service = new GitHubConnectionAppService(repository, verifier, cipher);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new GitHubConnectionController(service)).build();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void should_connectOnlyTheCurrentUserWithoutReturningTheCredential() throws Exception {
        UserContext.setCurrentUserId("owner-a");
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"repositories\":[\"alice/one\",\"alice/two\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.githubLogin").value("alice"))
                .andExpect(jsonPath("$.data.repositories.length()").value(2))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.encryptedToken").doesNotExist());

        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.data.repositories.length()").value(2));
        assertNotEquals(TOKEN, connections.get("owner-a").encryptedToken());
        UserContext.setCurrentUserId("owner-b");
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(delete(PATH)).andExpect(status().isOk());
        assertThrows(BusinessException.class,
                () -> service.withAuthorizedToken("owner-b", "alice/one", token -> token));
        UserContext.setCurrentUserId("owner-a");
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.data.repositories.length()").value(2));
    }

    @Test
    void should_narrowScopeAndRevokeBeforeAnyFutureUse() throws Exception {
        UserContext.setCurrentUserId("owner-a");
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"repositories\":[\"alice/one\",\"alice/two\"]}"));

        mvc.perform(put(PATH + "/repositories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositories\":[\"alice/one\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repositories.length()").value(1));
        assertThrows(BusinessException.class,
                () -> service.withAuthorizedToken("owner-a", "alice/two", token -> token));
        String usableToken = service.withAuthorizedToken("owner-a", "alice/one", token -> token);
        org.junit.jupiter.api.Assertions.assertEquals(TOKEN, usableToken);

        mvc.perform(delete(PATH)).andExpect(status().isOk());
        assertThrows(BusinessException.class,
                () -> service.withAuthorizedToken("owner-a", "alice/one", token -> token));
    }

    @Test
    void should_rejectScopeWideningWithoutReplacingTheConnection() throws Exception {
        UserContext.setCurrentUserId("owner-a");
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"repositories\":[\"alice/one\"]}"));

        assertThrows(BusinessException.class, () -> service.narrowRepositories("owner-a", List.of("alice/two")));
        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repositories[0]").value("alice/one"));
    }

    @Test
    void should_failClosedWithoutAnEncryptionKey() {
        GitHubConnectionAppService unconfigured = new GitHubConnectionAppService(repository, verifier,
                new GitHubTokenCipher(""));
        assertThrows(BusinessException.class, () -> unconfigured.connect("owner-a", TOKEN, List.of("alice/one")));
        org.junit.jupiter.api.Assertions.assertTrue(connections.isEmpty());
    }
}
