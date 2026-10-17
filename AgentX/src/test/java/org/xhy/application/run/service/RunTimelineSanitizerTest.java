package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunTimelineSanitizerTest {

    @Test
    void redactsCredentialFieldsWhilePreservingIssueReviewFieldsAndCitations() {
        String arguments = "{\"title\":\"Review me\",\"body\":\"Release facts [S1]\","
                + "\"repository\":\"owner/repo\",\"api_key\":\"apiSentinel1234567890\","
                + "\"headers\":{\"Cookie\":\"cookieSentinel1234567890\","
                + "\"User-Agent\":\"agentSentinel1234567890\"},"
                + "\"cookies\":{\"session\":\"sessionSentinel1234567890\"},"
                + "\"nested\":{\"clientSecret\":\"clientSentinel1234567890\"}}";

        String safe = RunTimelineSanitizer.redactJson(arguments);

        assertTrue(safe.contains("Review me"));
        assertTrue(safe.contains("Release facts [S1]"));
        assertTrue(safe.contains("owner/repo"));
        assertTrue(safe.contains("[REDACTED]"));
        assertFalse(safe.contains("apiSentinel1234567890"));
        assertFalse(safe.contains("clientSentinel1234567890"));
        assertFalse(safe.contains("cookieSentinel1234567890"));
        assertFalse(safe.contains("agentSentinel1234567890"));
        assertFalse(safe.contains("sessionSentinel1234567890"));
    }

    @Test
    void redactsInlineBearerAndKeyValueCredentialsInNonJsonText() {
        String safe = RunTimelineSanitizer
                .redactText("MCP failed: Authorization: Bearer bearerSentinel123 api_key=inlineSentinel123 "
                        + "headers={\"Cookie\":\"headerSentinel1234567890\",\"User-Agent\":\"agentSentinel1234567890\"}");

        assertTrue(safe.contains("[REDACTED]"));
        assertFalse(safe.contains("bearerSentinel123"));
        assertFalse(safe.contains("inlineSentinel123"));
        assertFalse(safe.contains("headerSentinel1234567890"));
        assertFalse(safe.contains("agentSentinel1234567890"));
    }

    @Test
    void redactsCredentialFieldsInTopLevelAndEmbeddedJsonTextWithoutHidingSafeFields() {
        String json = "{\"api_key\":\"opaqueApiSentinel1234567890\","
                + "\"authorization\":\"opaqueAuthSentinel1234567890\","
                + "\"headers\":{\"User-Agent\":\"opaqueHeaderSentinel1234567890\","
                + "\"Cookie\":\"opaqueHeaderCookieSentinel1234567890\"},"
                + "\"cookies\":{\"session\":\"opaqueCookieSentinel1234567890\"},"
                + "\"title\":\"Review me\",\"body\":\"Keep citation [S1]\"}";

        String topLevel = RunTimelineSanitizer.redactText(json);
        String embedded = RunTimelineSanitizer.redactText("Retrieved metadata: " + json + " Citation [S1].");

        for (String safe : new String[]{topLevel, embedded}) {
            assertFalse(safe.contains("opaqueApiSentinel1234567890"));
            assertFalse(safe.contains("opaqueAuthSentinel1234567890"));
            assertFalse(safe.contains("opaqueHeaderSentinel1234567890"));
            assertFalse(safe.contains("opaqueHeaderCookieSentinel1234567890"));
            assertFalse(safe.contains("opaqueCookieSentinel1234567890"));
            assertTrue(safe.contains("Review me"));
            assertTrue(safe.contains("Keep citation [S1]"));
        }
        assertTrue(embedded.startsWith("Retrieved metadata: "));
        assertTrue(embedded.endsWith(" Citation [S1]."));
    }

    @Test
    void redactsQuotedCredentialFieldsInTruncatedJsonText() {
        String truncated = "Retrieved: {\"api_key\":\"truncatedApiSentinel1234567890\","
                + "\"authorization\":\"truncatedAuthSentinel1234567890\","
                + "\"headers\":{\"Cookie\":\"truncatedCookieSentinel1234567890\"";

        String safe = RunTimelineSanitizer.redactText(truncated);

        assertFalse(safe.contains("truncatedApiSentinel1234567890"));
        assertFalse(safe.contains("truncatedAuthSentinel1234567890"));
        assertFalse(safe.contains("truncatedCookieSentinel1234567890"));
        assertTrue(safe.contains("[REDACTED]"));
    }

    @Test
    void preservesOrdinaryBracesAndHandlesBraceHeavyMalformedTextInLinearTime() {
        String ordinary = "Use {one} and {two}; these are examples, not JSON.";
        assertEquals(ordinary, RunTimelineSanitizer.redactText(ordinary));

        String malformed = "{".repeat(80_000);
        assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertEquals(malformed, RunTimelineSanitizer.redactText(malformed)));

        String repeatedSensitiveObjects = "{\"headers\":{\"Cookie\":\"secret\"}},".repeat(5_000);
        assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertFalse(RunTimelineSanitizer.redactText(repeatedSensitiveObjects).contains("secret")));
    }

    @Test
    void redactsCookieHeaderLinesInPromptsAndLogs() {
        String safe = RunTimelineSanitizer
                .redactText("Continue this task\nCookie: cookieLineSentinel1234567890\nNext line");

        assertTrue(safe.contains("Next line"));
        assertFalse(safe.contains("cookieLineSentinel1234567890"));
    }

    @Test
    void failsClosedForMalformedApprovalJsonContainingCredentialLikeText() {
        String safe = RunTimelineSanitizer.redactJson("not-json authorization=malformedSentinel123");

        assertFalse(safe.contains("malformedSentinel123"));
    }
}
