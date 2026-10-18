package org.xhy.application.github.service;

import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubIssueResultNormalizerTest {
    private final GitHubIssueResultNormalizer normalizer = new GitHubIssueResultNormalizer();

    @Test
    void normalizesStructuredMcpIssuePayloadToCanonicalIssueUrl() {
        String payload = """
                {"structuredContent":{"number":42,"html_url":"https://github.com/KumiKo2007/hi-agent/issues/42"},
                 "content":[{"type":"text","text":"Created issue #42"}],"isError":false}
                """;

        assertEquals("https://github.com/kumikO2007/hi-agent/issues/42".toLowerCase(java.util.Locale.ROOT),
                normalizer.normalize("github", "create_issue", "KumiKo2007/Hi-Agent", payload));
    }

    @Test
    void extractsIssueUrlFromMcpTextContent() {
        String payload = """
                {"content":[{"type":"text","text":"Created issue #57: https://github.com/octo/project/issues/57"}],
                 "isError":false}
                """;

        assertEquals("https://github.com/octo/project/issues/57",
                normalizer.normalize("github", "create_issue", "octo/project", payload));
    }

    @Test
    void createsCanonicalUrlFromStructuredIssueNumberOrPlainTextNumber() {
        assertEquals("https://github.com/octo/project/issues/11", normalizer.normalize("github", "create_issue",
                "octo/project", "{\"structuredContent\":{\"number\":11}}"));
        assertEquals("https://github.com/octo/project/issues/12",
                normalizer.normalize("github", "create_issue", "octo/project", "Created issue #12"));
    }

    @Test
    void convertsTheOfficialGitHubApiIssueUrlToItsBrowserUrl() {
        String payload = "{\"structuredContent\":{\"number\":42,"
                + "\"url\":\"https://api.github.com/repos/octo/project/issues/42\"}}";

        assertEquals("https://github.com/octo/project/issues/42",
                normalizer.normalize("github", "create_issue", "octo/project", payload));
    }

    @Test
    void ignoresTheCreatedBodyWhenLookingForTheResultIssueUrl() {
        String payload = "{\"structuredContent\":{\"number\":42,"
                + "\"body\":\"References https://github.com/another/project/blob/main/design.md\"}}";

        assertEquals("https://github.com/octo/project/issues/42",
                normalizer.normalize("github", "create_issue", "octo/project", payload));
    }

    @Test
    void rejectsForeignRepositoryAndPullRequestResultLinks() {
        String foreign = "{\"content\":[{\"type\":\"text\",\"text\":"
                + "\"Created issue https://github.com/attacker/private/issues/42\"}],"
                + "\"structuredContent\":{\"number\":42}}";
        String pullRequest = "Created pull request #42: https://github.com/octo/project/pull/42";
        String error = "{\"isError\":true,\"structuredContent\":{\"number\":42}}";

        assertThrows(BusinessException.class,
                () -> normalizer.normalize("github", "create_issue", "octo/project", foreign));
        assertThrows(BusinessException.class,
                () -> normalizer.normalize("github", "create_issue", "octo/project", pullRequest));
        assertThrows(BusinessException.class,
                () -> normalizer.normalize("github", "create_issue", "octo/project", error));
    }

    @Test
    void leavesNonGitHubActionResultsUntouched() {
        assertEquals("completed", normalizer.normalize("docs", "search", null, "completed"));
    }
}
