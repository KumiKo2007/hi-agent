package org.xhy.application.github.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubConnectionApprovalAuthorizationTest {
    private static final String OWNER = "owner-1";
    private static final String TOKEN = "github_pat_abcdefghijklmnopqrstuvwxyz123456";

    @Test
    void shouldRejectRepositoryOutsideSavedScopeBeforeRevalidatingToken() {
        Fixture fixture = new Fixture();

        assertThrows(BusinessException.class,
                () -> fixture.service.verifyAuthorizedRepository(OWNER, "outside-org/private"));

        assertEquals(0, fixture.verifications.get());
    }

    @Test
    void shouldRejectRevokedGitHubCredentialBeforeAnyActionCanRun() {
        Fixture fixture = new Fixture();
        fixture.revoked.set(true);

        assertThrows(BusinessException.class,
                () -> fixture.service.verifyAuthorizedRepository(OWNER, "KumiKo2007/hi-agent"));

        assertEquals(1, fixture.verifications.get());
        assertEquals(List.of("kumiko2007/hi-agent"), fixture.verifiedRepositories.get());
    }

    @Test
    void shouldRevalidateOwnerIdentityAndTheExactAllowedRepository() {
        Fixture fixture = new Fixture();

        fixture.service.verifyAuthorizedRepository(OWNER, "KumiKo2007/hi-agent");

        assertEquals(1, fixture.verifications.get());
        assertEquals(List.of("kumiko2007/hi-agent"), fixture.verifiedRepositories.get());
    }

    private static final class Fixture {
        private final AtomicInteger verifications = new AtomicInteger();
        private final AtomicReference<List<String>> verifiedRepositories = new AtomicReference<>();
        private final AtomicReference<Boolean> revoked = new AtomicReference<>(false);
        private final GitHubTokenCipher cipher = new GitHubTokenCipher(
                Base64.getEncoder().encodeToString(new byte[32]));
        private final GitHubConnectionRepository repository = new GitHubConnectionRepository() {
            private GitHubConnection connection = new GitHubConnection(OWNER, "github-user-1", "owner",
                    cipher.encrypt(OWNER, TOKEN), List.of("kumiko2007/hi-agent"));

            @Override public Optional<GitHubConnection> findByOwner(String ownerId) {
                return OWNER.equals(ownerId) ? Optional.of(connection) : Optional.empty();
            }
            @Override public Optional<GitHubConnection> findByOwnerForUpdate(String ownerId) {
                return findByOwner(ownerId);
            }
            @Override public void save(GitHubConnection value) { connection = value; }
            @Override public void deleteByOwner(String ownerId) { connection = null; }
        };
        private final GitHubCredentialVerifier verifier = (token, repositories) -> {
            verifications.incrementAndGet();
            if (!TOKEN.equals(token)) throw new AssertionError("Expected the saved GitHub token");
            verifiedRepositories.set(List.copyOf(repositories));
            if (revoked.get()) throw new BusinessException("credential was revoked");
            return new GitHubCredentialVerifier.Identity("github-user-1", "owner");
        };
        private final GitHubConnectionAppService service = new GitHubConnectionAppService(repository, verifier, cipher);
    }
}
