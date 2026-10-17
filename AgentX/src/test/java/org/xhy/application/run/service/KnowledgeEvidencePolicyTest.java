package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeEvidencePolicyTest {
    @Test
    void rejectsOppositePolarityWhenAnUnrelatedCitedSentenceContainsNegation() {
        KnowledgeCitation citation = citation(
                "The gateway retry failed requests. The cache does not expire early.");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsNaturalRetryPolarityReversalAcrossVerbInflections() {
        KnowledgeCitation citation = citation("The gateway retries failed requests.");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsMatchingNegatedClaimSupportedByTheSameCitationSentence() {
        KnowledgeCitation citation = citation("The gateway does not retry failed requests.");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsMatchingPositiveClaimSupportedByTheSameCitationSentence() {
        KnowledgeCitation citation = citation("The gateway retries failed requests.");
        String arguments = issue("Gateway retry", "The gateway retries failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsFactualTitleWithOppositePolarityEvenWhenBodyIsSupported() {
        KnowledgeCitation citation = citation("The gateway does not retry failed requests.");
        String arguments = issue("The gateway retries failed requests.",
                "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsPositiveFactualTitleSupportedByTheCitation() {
        KnowledgeCitation citation = citation("The gateway retries failed requests.");
        String arguments = issue("The gateway retries failed requests.",
                "The gateway retries failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsAvailableStatusTitleThatConflictsWithCitedSentence() {
        KnowledgeCitation citation = citation(
                "The gateway is not available during deployment. The cache expires after 30 days.");
        String arguments = issue("Gateway available during deployment", "The cache expires after 30 days.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsSupportedNounPhraseTitleAsAHeading() {
        KnowledgeCitation citation = citation(
                "The gateway retry behavior is documented. The gateway retries failed requests.");
        String arguments = issue("Gateway retry behavior", "The gateway retries failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsFactualTitlePredicateAfterMultiwordSubject() {
        KnowledgeCitation citation = citation("The gateway retry is not broken.");
        String arguments = issue("The gateway retry is broken.", "The gateway retry is not broken.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsReversedRelationshipWhenEveryClaimTermAppearsInEvidence() {
        KnowledgeCitation citation = citation("The gateway blocks token rotation.");
        String arguments = issue("Gateway token rotation", "Token rotation blocks the gateway.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsSupportedRelationshipInEvidenceTermOrder() {
        KnowledgeCitation citation = citation("The gateway blocks token rotation.");
        String arguments = issue("Gateway token rotation", "The gateway blocks token rotation.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsChineseTitleRelationshipWhoseBigramsAreSpreadAcrossCitedClauses() {
        KnowledgeCitation citation = citation("令牌轮换阻止系统。阻止网关运行。网关可用。");
        String arguments = issue("令牌轮换阻止网关", "网关可用");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsChineseTitleWhenOneCitedClauseSupportsItsOrder() {
        KnowledgeCitation citation = citation("令牌轮换阻止网关。网关可用。");
        String arguments = issue("令牌轮换阻止网关", "网关可用");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsChineseNounHeadingWhenOneCitedClauseSupportsItsOrder() {
        KnowledgeCitation citation = citation("网关令牌轮换资料已记录。网关运行稳定。");
        String arguments = issue("网关令牌轮换", "网关运行稳定");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsGatewayPolarityReversalWhenUnrelatedNegationFollowsACommaAndWhile() {
        KnowledgeCitation citation = citation(
                "The gateway retries failed requests, while the cache does not expire early.");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsGatewayPolarityReversalWhenUnrelatedNegationFollowsWhileWithoutComma() {
        KnowledgeCitation citation = citation(
                "The gateway retries failed requests while the cache does not expire early.");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void splitsChineseContrastiveMarkerBeforeCheckingGatewayPolarity() {
        KnowledgeCitation citation = citation(
                "The gateway retries failed requests，但是缓存不会提前过期。");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void treatsWithoutDelayAsAComplementRatherThanWholeClauseNegation() {
        KnowledgeCitation citation = citation("The gateway retries failed requests without delay.");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void acceptsSupportedWithoutComplementWithoutTreatingItAsClauseNegation() {
        KnowledgeCitation citation = citation("The gateway retries failed requests without authentication.");
        String arguments = issue("Gateway retry", "The gateway retries failed requests without authentication.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void rejectsWithAuthenticationWhenCitationSupportsWithoutAuthentication() {
        KnowledgeCitation citation = citation("The gateway retries failed requests without authentication.");
        String arguments = issue("Gateway retry", "The gateway retries failed requests with authentication.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void splitsStandaloneChineseButBeforeCheckingGatewayPolarity() {
        KnowledgeCitation citation = citation("The gateway retries failed requests但缓存不会提前过期");
        String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void splitsCommonChineseContrastMarkersBeforeCheckingGatewayPolarity() {
        for (String marker : List.of("而", "却")) {
            KnowledgeCitation citation = citation(
                    "The gateway retries failed requests" + marker + "缓存不会提前过期");
            String arguments = issue("Gateway retry", "The gateway does not retry failed requests.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), marker);
        }
    }

    @Test
    void treatsChineseNotOnlyIdiomAsPositiveGatewayEvidence() {
        KnowledgeCitation citation = citation("不但网关重试失败请求，而且缓存可用");
        String arguments = issue("网关重试失败请求", "网关重试失败请求");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void treatsNoMonitoringAsAComplementRatherThanWholeClauseNegation() {
        KnowledgeCitation citation = citation("The gateway retry is slow with no monitoring.");
        String arguments = issue("Gateway retry", "The gateway retry is not slow.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void preservesClauseLevelNoNegationForSubjectAndExistentialForms() {
        for (String content : List.of("No server is available.", "There is no server available.")) {
            KnowledgeCitation citation = citation(content);
            String arguments = issue("Server available", "A server is available.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), content);
        }
    }

    @Test
    void treatsPredicateNoAsNegationWithoutReclassifyingNoComplements() {
        for (String content : List.of("The gateway has no retries.", "Gateways have no retries.",
                "The gateway had no retries.")) {
            KnowledgeCitation citation = citation(content);
            String arguments = issue("Gateway retries", content.replace(" no ", " "));

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), content);
        }
    }

    @Test
    void treatsNoLongerAsNegation() {
        KnowledgeCitation citation = citation("The gateway no longer retries failed requests.");
        String arguments = issue("Gateway retry", "The gateway retries failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void treatsWithoutRetryFormsAsActionNegation() {
        for (String form : List.of("retry", "retries", "retrying")) {
            KnowledgeCitation citation = citation(
                    "The gateway operates without " + form + " for failed requests.");
            String arguments = issue("Gateway retry", "The gateway retries failed requests.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), form);
        }
    }

    @Test
    void treatsAvoidRetryFormsAsActionNegation() {
        for (String form : List.of("avoids", "avoided", "avoiding", "avoid")) {
            KnowledgeCitation citation = citation(
                    "The gateway " + form + " retries for failed requests.");
            String arguments = issue("Gateway retry", "The gateway retries failed requests.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), form);
        }
    }

    @Test
    void treatsProvideNoAsPredicateNegation() {
        for (String verb : List.of("provides", "provided")) {
            KnowledgeCitation citation = citation("The gateway " + verb + " no retry support.");
            String arguments = issue("Gateway retry support", "The gateway " + verb + " retry support.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), verb);
        }
    }

    @Test
    void treatsLackOfRetrySupportAsActionNegation() {
        for (String form : List.of("lack", "lacks", "lacked", "lacking")) {
            KnowledgeCitation citation = citation("The gateway " + form + " retry support.");
            String arguments = issue("Gateway retry support", "The gateway has retry support.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), form);
        }
    }

    @Test
    void distinguishesNotLackingRetrySupportFromLackingIt() {
        KnowledgeCitation positiveEvidence = citation(
                "The gateway is not lacking retry support for failed requests.");
        KnowledgeCitation negativeEvidence = citation("The gateway is lacking retry support for failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry support", "The gateway is not lacking retry support for failed requests."),
                List.of(positiveEvidence)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry support", "The gateway is lacking retry support for failed requests."),
                List.of(positiveEvidence)));
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry support", "The gateway is lacking retry support for failed requests."),
                List.of(negativeEvidence)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry support", "The gateway is not lacking retry support for failed requests."),
                List.of(negativeEvidence)));
    }

    @Test
    void treatsNegatedRetryRefusalAndInabilityPhrasesAsPositive() {
        List<String[]> cases = List.of(
                new String[] {"Gateways do not avoid retries for failed requests.", "Gateways",
                        "Gateways do not avoid retries for failed requests.",
                        "Gateways avoid retries for failed requests."},
                new String[] {"The gateway is not avoiding retries for failed requests.", "Gateway retry",
                        "The gateway is not avoiding retries for failed requests.",
                        "The gateway is avoiding retries for failed requests."},
                new String[] {"The gateway is not refusing to retry failed requests.",
                        "Gateway retry", "The gateway is not refusing to retry failed requests.",
                        "The gateway is refusing to retry failed requests."},
                new String[] {"The gateway is not unable to retry failed requests.",
                        "Gateway retry", "The gateway is not unable to retry failed requests.",
                        "The gateway is unable to retry failed requests."},
                new String[] {"The gateway is not stopping retries for failed requests.",
                        "Gateway retry", "The gateway is not stopping retries for failed requests.",
                        "The gateway is stopping retries for failed requests."},
                new String[] {"The gateway is not refraining from retrying failed requests.",
                        "Gateway retrying", "The gateway is not refraining from retrying failed requests.",
                        "The gateway is refraining from retrying failed requests."},
                new String[] {"The gateway does not lack the ability to retry failed requests.",
                        "Gateway retry", "The gateway retries failed requests.",
                        "The gateway lacks the ability to retry failed requests."},
                new String[] {"The gateway does not stop attempts to retry failed requests.",
                        "Gateway retry", "The gateway retries failed requests.",
                        "The gateway stops attempts to retry failed requests."},
                new String[] {"The gateway does not refrain from initiating retries for failed requests.",
                        "Gateway retry", "The gateway retries failed requests.",
                        "The gateway refrains from initiating retries for failed requests."});

        for (String[] testCase : cases) {
            KnowledgeCitation citation = citation(testCase[0]);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(issue(testCase[1], testCase[2]),
                    List.of(citation)), testCase[0] + " should support continued retries");
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue(testCase[1], testCase[3]),
                    List.of(citation)), testCase[0] + " must not support its opposite");
        }
    }

    @Test
    void treatsNotFailingToRetryAsPositiveEvidence() {
        KnowledgeCitation citation = citation("The gateway does not fail to retry failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)));
    }

    @Test
    void treatsNotFailingToContinueRetryingAsPositiveEvidence() {
        KnowledgeCitation citation = citation("The gateway does not fail to continue retrying failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway does not continue retrying failed requests."),
                List.of(citation)));
    }

    @Test
    void treatsDoNotStopAndDoNotRefrainFromRetryingAsPositive() {
        List<String[]> cases = List.of(
                new String[] {"The gateway does not stop retrying failed requests.",
                        "The gateway stops retrying failed requests."},
                new String[] {"The gateway does not refrain from retrying failed requests.",
                        "The gateway refrains from retrying failed requests."});
        for (String[] testCase : cases) {
            String evidence = testCase[0];
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retrying", "The gateway is retrying failed requests."),
                    List.of(citation(evidence))), evidence);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue("Gateway retrying", testCase[1]),
                    List.of(citation(evidence))), evidence + " must not support its opposite");
        }
    }

    @Test
    void keepsAnUnrelatedPositiveClauseSupportedAfterLackOfRetrySupport() {
        KnowledgeCitation citation = citation("The gateway lacks retry support; the cache is available.");
        String arguments = issue("Cache available", "The cache is available.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void treatsRefusalAndCessationOfRetryAsActionNegation() {
        List<String[]> cases = List.of(
                new String[] {"The gateway refuses to retry failed requests.", "Gateway retry",
                        "The gateway retries failed requests."},
                new String[] {"The gateway stops retrying failed requests.", "Gateway retrying",
                        "The gateway is retrying failed requests."},
                new String[] {"The gateway refrains from retrying failed requests.", "Gateway retrying",
                        "The gateway is retrying failed requests."},
                new String[] {"Workers stop retrying failed requests.", "Workers",
                        "Workers are retrying failed requests."},
                new String[] {"Workers refrain from retrying failed requests.", "Workers",
                        "Workers are retrying failed requests."});

        for (String[] testCase : cases) {
            KnowledgeCitation citation = citation(testCase[0]);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue(testCase[1], testCase[2]),
                    List.of(citation)), testCase[0]);
        }
    }

    @Test
    void treatsInterveningRetryActionWordsAsActionNegation() {
        List<String[]> cases = List.of(
                new String[] {"The gateway lacks the ability to retry failed requests.", "Gateway retry",
                        "The gateway retries failed requests."},
                new String[] {"The gateway stops attempts to retry failed requests.", "Gateway retry",
                        "The gateway retries failed requests."},
                new String[] {"The gateway refrains from initiating retries for failed requests.",
                        "Gateway retry", "The gateway retries failed requests."});

        for (String[] testCase : cases) {
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue(testCase[1], testCase[2]),
                    List.of(citation(testCase[0]))), testCase[0]);
        }
    }

    @Test
    void treatsNoRetriesAndUnableToRetryAsActionNegation() {
        for (String phrase : List.of("supports no retries", "offers no retries", "operates with no retries")) {
            KnowledgeCitation citation = citation("The gateway " + phrase + " for failed requests.");
            String arguments = issue("Gateway retry", "The gateway retries failed requests.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)), phrase);
        }

        KnowledgeCitation citation = citation("The gateway is unable to retry failed requests.");
        String arguments = issue("Gateway retry", "The gateway retries failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void treatsIncapableOfRetryingAsActionNegation() {
        KnowledgeCitation citation = citation("The gateway is incapable of retrying failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(citation)));
        KnowledgeCitation doubleNegatedCitation = citation(
                "The gateway is not incapable of retrying failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(doubleNegatedCitation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is not retrying failed requests."),
                List.of(doubleNegatedCitation)));
    }

    @Test
    void treatsInabilityToKeepOrPerformRetriesAsActionNegation() {
        for (String action : List.of("keep retrying", "perform retries", "attempt to retry", "initiate retries")) {
            KnowledgeCitation citation = citation("The gateway is unable to " + action + " failed requests.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(citation)), action);
        }
    }

    @Test
    void treatsChainedAndGerundRetryInabilityAsActionNegation() {
        for (String evidence : List.of(
                "The gateway is unable to continue performing retries for failed requests.",
                "The gateway is incapable of initiating retries for failed requests.",
                "The gateway is unable to successfully retry failed requests.")) {
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retries", "The gateway retries failed requests."), List.of(citation(evidence))),
                    evidence);
        }
    }

    @Test
    void treatsNotUnableToKeepRetryingAsPositiveEvidence() {
        KnowledgeCitation citation = citation("The gateway is not unable to keep retrying failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is not retrying failed requests."), List.of(citation)));
    }

    @Test
    void treatsNotUnableToSuccessfullyRetryAsPositiveEvidence() {
        KnowledgeCitation citation = citation("The gateway is not unable to successfully retry failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)));
    }

    @Test
    void doesNotTreatInabilityToOnlyRetryAsInabilityToRetry() {
        KnowledgeCitation citation = citation("The gateway is unable to only retry failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)));
    }

    @Test
    void treatsDecliningToRetryAsActionNegation() {
        List<String[]> cases = List.of(
                new String[] {"The gateway declines to retry failed requests.", "Gateway retry",
                        "The gateway retries failed requests.", "The gateway does not retry failed requests."},
                new String[] {"The gateway declines to initiate retries for failed requests.", "Gateway retries",
                        "The gateway can initiate retries for failed requests.",
                        "The gateway does not initiate retries for failed requests."},
                new String[] {"The gateway refuses to initiate retries for failed requests.", "Gateway retries",
                        "The gateway can initiate retries for failed requests.",
                        "The gateway does not initiate retries for failed requests."});
        for (String[] testCase : cases) {
            KnowledgeCitation citation = citation(testCase[0]);

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[2]), List.of(citation)), testCase[0]);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[3]), List.of(citation)), testCase[0]);
        }
    }

    @Test
    void treatsNotDecliningToRetryAsPositiveEvidence() {
        for (String evidence : List.of(
                "The gateway does not decline to initiate retries for failed requests.",
                "The gateway does not refuse to initiate retries for failed requests.")) {
            KnowledgeCitation citation = citation(evidence);

            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retries", "The gateway can initiate retries for failed requests."),
                    List.of(citation)), evidence);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retries", "The gateway does not initiate retries for failed requests."),
                    List.of(citation)), evidence);
        }
    }

    @Test
    void treatsCannotStopRetriesAsPositiveEvidence() {
        KnowledgeCitation citation = citation("The gateway cannot stop retries for failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway cannot retry failed requests."), List.of(citation)));
    }

    @Test
    void treatsPreventingRetriesAsActionNegation() {
        List<String[]> cases = List.of(
                new String[] {"The gateway prevents retries for failed requests.", "Gateway retries",
                        "The gateway retries failed requests.", "The gateway does not retry failed requests."},
                new String[] {"Circuit breakers prevent the gateway from retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."},
                new String[] {"The gateway is prevented from retrying failed requests.", "Gateway retrying",
                        "The gateway is retrying failed requests.", "The gateway is not retrying failed requests."},
                new String[] {"The gateway is prevented by a circuit breaker from retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."},
                new String[] {"Circuit breakers prevent the gateway from ever retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."});
        for (String[] testCase : cases) {
            KnowledgeCitation citation = citation(testCase[0]);

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[2]), List.of(citation)), testCase[0]);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[3]), List.of(citation)), testCase[0]);
        }
    }

    @Test
    void treatsNotPreventingRetriesAsPositiveEvidence() {
        List<String[]> cases = List.of(
                new String[] {"The gateway does not prevent retries for failed requests.", "Gateway retries",
                        "The gateway retries failed requests.", "The gateway does not retry failed requests."},
                new String[] {"The gateway does not prevent the gateway from retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."},
                new String[] {"The gateway does not prevent the gateway from ever retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."},
                new String[] {"The gateway is not prevented from retrying failed requests.", "Gateway retrying",
                        "The gateway is retrying failed requests.", "The gateway is not retrying failed requests."},
                new String[] {"The gateway is not prevented by a circuit breaker from retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."});
        for (String[] testCase : cases) {
            KnowledgeCitation citation = citation(testCase[0]);

            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[2]), List.of(citation)), testCase[0]);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[3]), List.of(citation)), testCase[0]);
        }
    }

    @Test
    void treatsBeingAbleToRetryAsAPreventedAction() {
        KnowledgeCitation preventsRetry = citation(
                "Circuit breakers prevent the gateway from being able to retry failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway can retry failed requests."), List.of(preventsRetry)));
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(preventsRetry)));

        KnowledgeCitation doesNotPreventRetry = citation(
                "The gateway does not prevent the gateway from being able to retry failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway can retry failed requests."), List.of(doesNotPreventRetry)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(doesNotPreventRetry)));
    }

    @Test
    void treatsPreventingNegatedRetryActionAsPositiveEvidence() {
        List<String[]> cases = List.of(
                new String[] {"Circuit breakers prevent the gateway from not retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."},
                new String[] {"The gateway is prevented by a circuit breaker from not retrying failed requests.",
                        "Gateway retrying", "The gateway is retrying failed requests.",
                        "The gateway is not retrying failed requests."},
                new String[] {"Circuit breakers prevent the gateway from failing to retry failed requests.",
                        "Gateway retry", "The gateway retries failed requests.",
                        "The gateway does not retry failed requests."},
                new String[] {"The gateway is prevented by a circuit breaker from failing to retry failed requests.",
                        "Gateway retry", "The gateway retries failed requests.",
                        "The gateway does not retry failed requests."},
                new String[] {"Circuit breakers prevent the retry handler from not retrying failed requests.",
                        "Retry handler retrying", "The retry handler is retrying failed requests.",
                        "The retry handler is not retrying failed requests."},
                new String[] {"The policy prevents clients from failing to retry failed requests.",
                        "Clients retry", "Clients retry failed requests.",
                        "Clients do not retry failed requests."});
        for (String[] testCase : cases) {
            KnowledgeCitation citation = citation(testCase[0]);

            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[2]), List.of(citation)), testCase[0]);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue(testCase[1], testCase[3]), List.of(citation)), testCase[0]);
        }
    }

    @Test
    void keepsThePreventedTargetBoundToItsSubject() {
        KnowledgeCitation citation = citation(
                "The gateway prevents the cache from not retrying failed requests.");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(citation)));
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Cache retrying", "The cache is retrying failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue("Gateway can retry",
                "The gateway can retry when the cache retries."), List.of(citation)));

        KnowledgeCitation nounOnlyRetry = citation("The gateway retry policy covers failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retrying", "The gateway is retrying failed requests."), List.of(nounOnlyRetry)));
        KnowledgeCitation countedRetryNoun = citation(
                "The gateway retry policy allows three retries for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(countedRetryNoun)));
        KnowledgeCitation unlimitedRetryNoun = citation(
                "The gateway retry policy permits unlimited retries for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(unlimitedRetryNoun)));
        KnowledgeCitation infiniteRetryNoun = citation(
                "The gateway retry policy allows infinite retries for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(infiniteRetryNoun)));
        KnowledgeCitation constrainedRetryNoun = citation(
                "The gateway retries are limited to three failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(constrainedRetryNoun)));
        KnowledgeCitation labeledRetryCount = citation("The gateway retries: 3 attempts for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(labeledRetryCount)));
        KnowledgeCitation modalUnlimitedRetryNoun = citation(
                "The gateway retries can be unlimited for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(modalUnlimitedRetryNoun)));
        KnowledgeCitation modalConfiguredRetryNoun = citation(
                "The gateway retries can be configured for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(modalConfiguredRetryNoun)));
        KnowledgeCitation modalSetRetryNoun = citation(
                "The gateway retries can be set to unlimited for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(modalSetRetryNoun)));
        KnowledgeCitation modalEnabledRetryNoun = citation(
                "The gateway retries can be enabled for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(modalEnabledRetryNoun)));
        KnowledgeCitation modalRemainingRetryNoun = citation(
                "The gateway retries should remain unlimited for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(modalRemainingRetryNoun)));
        KnowledgeCitation modalPossibleRetryNoun = citation(
                "The gateway retries may occur for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(modalPossibleRetryNoun)));
        KnowledgeCitation emptyRetryCount = citation("The gateway retries: none for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retries", "The gateway retries failed requests."), List.of(emptyRetryCount)));

        KnowledgeCitation retryPolicyAndSubordinateRetry = citation("The gateway retry policy prevents the cache "
                + "from not retrying requests when the cache retries.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue("Gateway retries",
                "The gateway retries requests when the cache retries."), List.of(retryPolicyAndSubordinateRetry)));

        KnowledgeCitation nounAfterPredicate = citation("The gateway retry controller prevents the cache from not "
                + "retrying requests for the cache retry controller.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(issue("Gateway retry controller retrying",
                "The gateway retry controller is retrying requests for the cache retry controller."),
                List.of(nounAfterPredicate)));

        KnowledgeCitation pluralRetryHandlers = citation(
                "Circuit breakers prevent the retry handlers from not retrying failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(issue("Retry handlers retry",
                "The retry handlers retry failed requests."), List.of(pluralRetryHandlers)));
    }

    @Test
    void preservesRetryModalityAndRejectsEmptyCountLabels() {
        for (String possibleAction : List.of(
                "The gateway can retry failed requests.",
                "The gateway may retry failed requests.")) {
            KnowledgeCitation citation = citation(possibleAction);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)),
                    possibleAction);
        }

        KnowledgeCitation possibleAction = citation("The gateway can retry failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway can retry", "The gateway can retry failed requests."), List.of(possibleAction)));

        KnowledgeCitation mayRetry = citation("The gateway may retry failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway may retry failed requests."), List.of(mayRetry)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway will retry failed requests."), List.of(mayRetry)));
        KnowledgeCitation willRetry = citation("The gateway will retry failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway may retry failed requests."), List.of(willRetry)));
        KnowledgeCitation futureRetryAbility = citation("The gateway will be able to retry failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(futureRetryAbility)));
        KnowledgeCitation capableRetry = citation("The gateway is capable of retrying failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(capableRetry)));
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway is capable of retrying failed requests."), List.of(capableRetry)));
        KnowledgeCitation futureCapability = citation("The gateway will be capable of retrying failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(futureCapability)));
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway will be capable of retrying failed requests."),
                List.of(futureCapability)));
        for (String uncertainInability : List.of(
                "may not be capable of retrying", "might be incapable of retrying")) {
            KnowledgeCitation citation = citation("The gateway " + uncertainInability + " failed requests.");
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)),
                    uncertainInability);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway " + uncertainInability + " failed requests."),
                    List.of(citation)), uncertainInability);
        }

        KnowledgeCitation unconfigurableRetryNoun = citation(
                "The gateway retries cannot be configured for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(unconfigurableRetryNoun)));

        KnowledgeCitation emptyRetryLabel = citation(
                "The gateway retries: no retries for failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(emptyRetryLabel)));
    }

    @Test
    void doesNotTreatConfiguredOrPlannedRetryInfinitivesAsCompletedRetries() {
        for (String infinitive : List.of("is configured to retry", "plans to retry")) {
            KnowledgeCitation citation = citation("The gateway " + infinitive + " failed requests.");

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)), infinitive);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway " + infinitive + " failed requests."), List.of(citation)),
                    infinitive);
        }

        KnowledgeCitation negativeIntent = citation("The gateway does not plan to retry failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(negativeIntent)));
    }

    @Test
    void preservesModalAndIntentMeaningForInflectedRetryActions() {
        for (String phrase : List.of(
                "will be retrying", "is planning on retrying", "is planning on eventually retrying",
                "may not be retrying")) {
            KnowledgeCitation citation = citation("The gateway " + phrase + " failed requests.");
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)), phrase);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway " + phrase + " failed requests."), List.of(citation)),
                    phrase);
        }

        KnowledgeCitation uncertainNegation = citation("The gateway may not be retrying failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(uncertainNegation)));
    }

    @Test
    void preservesModalMeaningAcrossNegationAndAdverbs() {
        for (String phrase : List.of(
                "may still retry", "may not retry", "may occasionally retry", "may continue retrying",
                "may keep retrying", "will probably retry")) {
            KnowledgeCitation citation = citation("The gateway " + phrase + " failed requests.");
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)), phrase);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway " + phrase + " failed requests."), List.of(citation)),
                    phrase);
        }

        KnowledgeCitation uncertainNegative = citation("The gateway may not retry failed requests.");
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(uncertainNegative)));

        KnowledgeCitation definiteInability = citation("The gateway cannot actually retry failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(definiteInability)));
    }

    @Test
    void doesNotTreatQualifiedInabilityAsAbsoluteRetryDenial() {
        for (String claim : List.of(
                "The gateway cannot always retry failed requests.",
                "The gateway cannot usually retry failed requests.",
                "The gateway cannot reliably retry failed requests.",
                "The gateway cannot successfully retry failed requests.",
                "The gateway cannot continue retrying failed requests.",
                "The gateway cannot keep retrying failed requests.",
                "The gateway cannot retry failed requests reliably.",
                "The gateway cannot retry failed requests successfully.",
                "The gateway cannot retry all failed requests.",
                "The gateway cannot retry in every case.",
                "The gateway cannot retry the entire batch.",
                "The gateway cannot retry failed requests unless configured.",
                "The gateway cannot retry failed requests after a timeout.",
                "The gateway cannot retry failed requests in production.",
                "The gateway cannot retry failed requests on older firmware.",
                "The gateway offers no retry support for failed requests in production.",
                "The gateway cannot retry failed requests for enterprise customers.",
                "The gateway refuses to retry failed requests in production.",
                "The gateway refuses to retry failed requests on older firmware.",
                "The gateway refuses to retry some failed requests.",
                "The gateway refuses to retry one of the failed requests.",
                "The gateway refuses to retry a single failed request.",
                "The gateway refuses to retry a subset of failed requests.",
                "The gateway refuses to retry a fraction of failed requests.",
                "The gateway refuses to retry a portion of failed requests.",
                "The gateway refuses to retry part of the failed requests.",
                "The gateway cannot retry failed requests under load.",
                "The gateway cannot retry failed requests under heavy load.")) {
            KnowledgeCitation citation = citation(claim);

            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)),
                    claim);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", claim), List.of(citation)), claim);
        }

        KnowledgeCitation noRequestCanRetry = citation("The gateway cannot retry any failed requests.");
        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(noRequestCanRetry)));
        for (String citationText : List.of(
                "The gateway certainly cannot retry failed requests.",
                "Certainly, the gateway cannot retry failed requests.",
                "With certainty, the gateway refuses to retry failed requests.",
                "Without fail, the gateway refuses to retry failed requests.",
                "Without exception, the gateway refuses to retry failed requests.",
                "Without a doubt, the gateway refuses to retry failed requests.",
                "Without any doubt, the gateway refuses to retry failed requests.")) {
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway does not retry failed requests."),
                    List.of(citation(citationText))), citationText);
        }
    }

    @Test
    void preservesScopeAcrossPositiveRetryAssertions() {
        for (String[] testCase : List.of(
                new String[] {"The gateway retries failed requests in production.",
                        "The gateway retries failed requests.", "The gateway retries failed requests in production."},
                new String[] {"The gateway does not stop retrying failed requests in production.",
                        "The gateway retries failed requests.", "The gateway retries failed requests in production."},
                new String[] {"The gateway can retry failed requests in production.",
                        "The gateway can retry failed requests.", "The gateway can retry failed requests in production."})) {
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", testCase[1]), List.of(citation(testCase[0]))), testCase[0]);
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", testCase[2]), List.of(citation(testCase[0]))), testCase[0]);
        }

        for (String citationText : List.of(
                "The gateway retries failed requests one at a time.",
                "The gateway retries failed requests one by one.")) {
            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway retries failed requests."), List.of(citation(citationText))),
                    citationText);
        }
    }

    @Test
    void doesNotDropLeadingConditionalScopeBeforeRetryDenials() {
        for (String citationText : List.of(
                "Unless the circuit breaker is open, the gateway cannot retry failed requests.",
                "Unless the circuit breaker is open the gateway cannot retry failed requests.",
                "Unless the circuit breaker is open and the tenant is enterprise, the gateway cannot retry failed requests.",
                "For some tenants, the gateway cannot retry failed requests.",
                "During deployment, the gateway cannot retry failed requests.",
                "In production, the gateway cannot retry failed requests.",
                "On older firmware, the gateway cannot retry failed requests.",
                "Only in production, the gateway cannot retry failed requests.",
                "Only for enterprise customers, the gateway cannot retry failed requests.",
                "For enterprise customers, the gateway cannot retry failed requests.",
                "Under load, the gateway cannot retry failed requests.",
                "Only under heavy load, the gateway cannot retry failed requests.",
                "Under heavy load, the gateway cannot retry failed requests.",
                "The gateway in production cannot retry failed requests.")) {
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway does not retry failed requests."),
                    List.of(citation(citationText))), citationText);
        }
    }

    @Test
    void resetsLeadingRetryScopeAtSemicolon() {
        KnowledgeCitation citation = citation(
                "Only in production, the gateway is unavailable; the cache cannot retry failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Cache retry", "The cache does not retry failed requests."), List.of(citation)));
    }

    @Test
    void resetsLeadingRetryScopeAtNewline() {
        KnowledgeCitation citation = citation(
                "Only in production, the gateway is unavailable\nThe cache cannot retry failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Cache retry", "The cache does not retry failed requests."), List.of(citation)));
    }

    @Test
    void keepsAnUnrelatedPositiveClauseAfterRetryRefusalSupported() {
        KnowledgeCitation citation = citation(
                "The gateway refuses to retry failed requests, while the cache remains available.");
        String arguments = issue("Cache available", "The cache remains available.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    @Test
    void treatsWithNoDelayAsAnAncillaryComplementRatherThanRetryNegation() {
        KnowledgeCitation citation = citation("The gateway retries failed requests with no delay.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests with no delay."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)));
    }

    @Test
    void treatsWithNoRetryLimitAsAConfigurationLimitRatherThanRetryNegation() {
        KnowledgeCitation citation = citation("The gateway retries failed requests with no retry limit.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests with no retry limit."),
                List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)));
    }

    @Test
    void treatsRetryDelayLatencyTimeoutAndBackoffAsConfigurationComplements() {
        for (String detail : List.of("with no retry delay", "without retry latency", "without a retry timeout",
                "without delay", "without a retry backoff", "without a retry back-off")) {
            KnowledgeCitation citation = citation("The gateway retries failed requests " + detail + ".");

            assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(issue("Gateway retry",
                    "The gateway retries failed requests " + detail + "."), List.of(citation)), detail);
            assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                    issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)),
                    detail);
        }
    }

    @Test
    void treatsWithoutARetryLimitAsAConfigurationLimitRatherThanRetryNegation() {
        KnowledgeCitation citation = citation("The gateway retries failed requests without a retry limit.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests without a retry limit."),
                List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)));
    }

    @Test
    void treatsSupportedNoRetryLimitAsConfigurationRatherThanNoRetryCapability() {
        KnowledgeCitation citation = citation("The gateway supports no retry limit for failed requests.");

        assertTrue(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway retries failed requests."), List.of(citation)));
        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(
                issue("Gateway retry", "The gateway does not retry failed requests."), List.of(citation)));
    }

    @Test
    void treatsChineseDeBuDeAsObligationRatherThanNegation() {
        KnowledgeCitation citation = citation("网关不得不重试请求");
        String arguments = issue("网关不得不重试请求", "网关不重试请求");

        assertFalse(KnowledgeEvidencePolicy.supportsDraftClaims(arguments, List.of(citation)));
    }

    private static KnowledgeCitation citation(String content) {
        KnowledgeCitation citation = new KnowledgeCitation();
        citation.setContent(content);
        return citation;
    }

    private static String issue(String title, String body) {
        return "{\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
    }
}
