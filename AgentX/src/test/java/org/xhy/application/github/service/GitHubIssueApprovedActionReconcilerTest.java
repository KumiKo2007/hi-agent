package org.xhy.application.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.xhy.application.run.service.ApprovedActionReconciliation;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.infrastructure.github.GitHubApiTransport;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubHttpApiTransport;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubIssueApprovedActionReconcilerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REPOSITORY = "owner/repo";

    @Test
    void shouldRecordOnlyAnExactIssueMarkerMatchAndSendAnAllowlistedSearchPath() throws Exception {
        Fixture fixture = new Fixture();
        String marker = fixture.marker();
        fixture.response.set(JSON.readTree("""
                {"items":[{"body":"Issue details\\n\\n%s","repository_url":"https://api.github.com/repos/owner/repo",
                  "html_url":"https://github.com/owner/repo/issues/42"}]}
                """.formatted(marker)));

        ApprovedActionReconciliation result = fixture.reconciler().reconcile(fixture.run(), fixture.approval());

        assertEquals(ApprovedActionReconciliation.Outcome.VERIFIED_CREATED, result.outcome());
        assertEquals("https://github.com/owner/repo/issues/42", result.externalResourceId());
        assertTrue(GitHubHttpApiTransport.isAllowedGetPath(fixture.path.get()));
        assertEquals("/search/issues?q=repo%3Aowner%2Frepo%20in%3Abody%20%22hi-agent-action-intent%3A"
                + fixture.approval().getId() + "%22", fixture.path.get());
    }

    @Test
    void shouldTreatAnEmptySearchResultAsUnknown() throws Exception {
        Fixture fixture = new Fixture();
        fixture.response.set(JSON.readTree("{\"total_count\":0,\"items\":[]}"));

        ApprovedActionReconciliation result = fixture.reconciler().reconcile(fixture.run(), fixture.approval());

        assertEquals(ApprovedActionReconciliation.Outcome.UNKNOWN, result.outcome());
        assertTrue(result.evidence().contains("does not prove"));
    }

    @Test
    void shouldNotTreatPullRequestSearchResultsAsCreatedIssues() throws Exception {
        Fixture fixture = new Fixture();
        fixture.response.set(JSON.readTree("""
                {"items":[{"body":"%s","repository_url":"https://api.github.com/repos/owner/repo",
                  "html_url":"https://github.com/owner/repo/pull/42","pull_request":{"url":"..."}}]}
                """.formatted(fixture.marker())));

        ApprovedActionReconciliation result = fixture.reconciler().reconcile(fixture.run(), fixture.approval());

        assertEquals(ApprovedActionReconciliation.Outcome.UNKNOWN, result.outcome());
    }

    private static final class Fixture {
        private final AtomicReference<JsonNode> response = new AtomicReference<>();
        private final AtomicReference<String> path = new AtomicReference<>();
        private final String intentId = UUID.randomUUID().toString();
        private final String marker = "<!-- hi-agent-action-intent:" + intentId + " -->";
        private final GitHubTokenCipher cipher = new GitHubTokenCipher(
                Base64.getEncoder().encodeToString(new byte[32]));
        private final GitHubConnectionAppService connections;
        private final RunApprovalEntity approval;

        private Fixture() {
            String token = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
            GitHubConnection connection = new GitHubConnection("user-1", "gh-user", "owner",
                    cipher.encrypt("user-1", token), List.of(REPOSITORY));
            GitHubConnectionRepository repository = new GitHubConnectionRepository() {
                @Override
                public Optional<GitHubConnection> findByOwner(String ownerId) {
                    return "user-1".equals(ownerId) ? Optional.of(connection) : Optional.empty();
                }
                @Override
                public Optional<GitHubConnection> findByOwnerForUpdate(String ownerId) {
                    return findByOwner(ownerId);
                }
                @Override
                public void save(GitHubConnection value) {
                }
                @Override
                public void deleteByOwner(String ownerId) {
                }
            };
            GitHubCredentialVerifier verifier = (savedToken,
                    repositories) -> new GitHubCredentialVerifier.Identity("gh-user", "owner");
            connections = new GitHubConnectionAppService(repository, verifier, cipher);
            String args = "{\"repository\":\"owner/repo\",\"title\":\"title\",\"body\":\"" + marker + "\"}";
            approval = RunApprovalEntity.create("run-1", new RunApprovalDraft("github-service", "create_issue",
                    "Create issue", REPOSITORY, args, Map.of("type", "object"), intentId));
        }

        private GitHubIssueApprovedActionReconciler reconciler() {
            GitHubApiTransport api = (token, requestPath) -> {
                assertEquals("github_pat_abcdefghijklmnopqrstuvwxyz123456", token);
                path.set(requestPath);
                return response.get();
            };
            return new GitHubIssueApprovedActionReconciler(connections, api);
        }

        private RunEntity run() {
            return RunEntity.create("user-1", "session-1", "agent-1", "goal");
        }

        private RunApprovalEntity approval() {
            return approval;
        }
        private String marker() {
            return marker;
        }
    }
}
