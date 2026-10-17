package org.xhy.application.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.xhy.application.run.service.ApprovedActionReconciler;
import org.xhy.application.run.service.ApprovedActionReconciliation;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.infrastructure.github.GitHubApiTransport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reconciles GitHub Issue creates only through the immutable body marker in approved arguments. */
@Component
public class GitHubIssueApprovedActionReconciler implements ApprovedActionReconciler {
    private static final Pattern MARKER = Pattern.compile("<!-- hi-agent-action-intent:[0-9a-fA-F-]{36} -->");
    private final GitHubConnectionAppService connections;
    private final GitHubApiTransport github;

    public GitHubIssueApprovedActionReconciler(GitHubConnectionAppService connections, GitHubApiTransport github) {
        this.connections = connections;
        this.github = github;
    }

    @Override
    public ApprovedActionReconciliation reconcile(RunEntity run, RunApprovalEntity approval) {
        String marker = markerFrom(approval);
        if (marker == null || !"create_issue".equals(approval.getActionName())) {
            return ApprovedActionReconciliation.unknown("Approved arguments have no supported immutable issue marker.");
        }
        String searchTerm = "hi-agent-action-intent:" + approval.getId();
        String query = "repo:" + approval.getTargetRepository() + " in:body \"" + searchTerm + "\"";
        String path = "/search/issues?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        try {
            JsonNode results = connections.withAuthorizedToken(run.getUserId(), approval.getTargetRepository(),
                    token -> github.get(token, path));
            List<JsonNode> matches = exactMatches(results.path("items"), approval.getTargetRepository(), marker);
            if (matches.size() == 1) {
                String resourceId = matches.get(0).path("html_url").asText();
                if (!resourceId.isBlank()) {
                    return ApprovedActionReconciliation.created(resourceId,
                            "GitHub returned an Issue in the authorized repository containing the exact immutable action marker.");
                }
            }
            if (matches.size() > 1) {
                return ApprovedActionReconciliation
                        .unknown("Multiple GitHub Issues contain the action marker; owner review is required.");
            }
            // GitHub search is indexed and may lag; a miss is never evidence of no write.
            return ApprovedActionReconciliation.unknown(
                    "No marker match was returned by GitHub search; this does not prove the write was not created.");
        } catch (RuntimeException exception) {
            return ApprovedActionReconciliation
                    .unknown("GitHub could not verify the action marker; owner review is required.");
        }
    }

    private String markerFrom(RunApprovalEntity approval) {
        if (approval == null || approval.getArguments() == null || approval.getId() == null) {
            return null;
        }
        String expected = "<!-- hi-agent-action-intent:" + approval.getId() + " -->";
        return MARKER.matcher(approval.getArguments()).find()
                ? (approval.getArguments().contains(expected) ? expected : null)
                : null;
    }

    private List<JsonNode> exactMatches(JsonNode items, String repository, String marker) {
        List<JsonNode> matches = new ArrayList<>();
        if (!items.isArray()) {
            return matches;
        }
        for (JsonNode item : items) {
            String body = item.path("body").asText("");
            String repositoryUrl = item.path("repository_url").asText("");
            String htmlUrl = item.path("html_url").asText("");
            String expectedRepositoryUrl = "https://api.github.com/repos/" + repository;
            boolean issueUrl = htmlUrl.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/issues/[0-9]+");
            if (item.path("pull_request").isMissingNode() && issueUrl && body.contains(marker)
                    && repositoryUrl.equalsIgnoreCase(expectedRepositoryUrl)) {
                matches.add(item);
            }
        }
        return matches;
    }
}
