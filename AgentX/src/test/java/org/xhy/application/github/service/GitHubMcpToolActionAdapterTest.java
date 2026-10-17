package org.xhy.application.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.tool.constant.ToolActionClassification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubMcpToolActionAdapterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                    Map.of("type", "string")),
            "required", List.of("repository", "title"), "additionalProperties", false);

    @Test
    void hidesGitHubToolsUntilTheRunSelectsAnAuthorizedRepository() {
        GitHubMcpToolActionAdapter adapter = new GitHubMcpToolActionAdapter();

        assertFalse(adapter.isVisible(action(), null));
        assertFalse(adapter.isVisible(action(), "  "));
        assertTrue(adapter.isVisible(action(), "owner/repo"));
    }

    @Test
    void addsTheSameIntentIdToTheFrozenIssueBody() throws Exception {
        GitHubMcpToolActionAdapter adapter = new GitHubMcpToolActionAdapter();
        String intentId = UUID.randomUUID().toString();

        String prepared = adapter.prepareApprovalArguments(action(), SCHEMA,
                "{\"repository\":\"owner/repo\",\"title\":\"Title\",\"body\":\"Details\"}", intentId);
        JsonNode args = JSON.readTree(prepared);

        assertEquals("Details\n\n<!-- hi-agent-action-intent:" + intentId + " -->", args.path("body").asText());
    }

    private RegisteredToolAction action() {
        return new RegisteredToolAction("github-service", "github", "create_issue", "Create issue", SCHEMA,
                ToolActionClassification.WRITE_REQUIRES_APPROVAL);
    }
}
