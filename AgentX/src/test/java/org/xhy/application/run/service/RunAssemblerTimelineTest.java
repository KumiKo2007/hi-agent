package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.application.run.assembler.RunAssembler;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunFailureCode;
import org.xhy.domain.run.model.RunTimelineEventData;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.run.model.RunTimelineEventType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunAssemblerTimelineTest {
    private static final String SENTINEL = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
    private static final String COOKIE_SENTINEL = "cookieSentinelabcdefghijklmnopqrstuvwxyz";

    @Test
    void shouldProjectOnlySafeTimelineMetadataAndRedactApprovalCredentials() {
        RunEntity run = RunEntity.create("owner", "session", "agent", "Summarize the release", "knowledge-v1");
        run.setStatus("FAILED");
        run.setGoal("Summarize api_key=" + SENTINEL + "\nCookie: " + COOKIE_SENTINEL);
        run.setResult("Answer Bearer abcdefghijklmnop\nSet-Cookie: " + COOKIE_SENTINEL);

        RunApprovalEntity approval = new RunApprovalEntity();
        approval.setId("approval-1");
        approval.setServiceId("github-service");
        approval.setActionName("create_issue");
        approval.setTargetRepository("octo/project");
        approval.setArguments("{\"repository\":\"octo/project\",\"title\":\"Review plan\","
                + "\"body\":\"See [S1]\",\"api_key\":\"" + SENTINEL + "\"," + "\"headers\":{\"Cookie\":\""
                + COOKIE_SENTINEL + "\",\"User-Agent\":\"agentSentinelabcdefghijklmnopqrstuvwxyz\"},"
                + "\"cookies\":{\"session\":\"sessionSentinelabcdefghijklmnopqrstuvwxyz\"}}");

        RunTimelineEventEntity modelCall = RunTimelineEventEntity.create("run", "step", RunTimelineEventType.MODEL_CALL,
                "COMPLETED", RunTimelineEventData.modelUsage("safe-model", 13, 5, true, 17));
        RunTimelineEventEntity retrieval = RunTimelineEventEntity.create("run", "step",
                RunTimelineEventType.KNOWLEDGE_SOURCE, "USED",
                RunTimelineEventData.source("knowledge-v1", "doc-1", "file-1", "guide.md", 2, "/rag/datasets/1"));
        RunTimelineEventEntity failure = RunTimelineEventEntity.create("run", "step", RunTimelineEventType.RUN_FAILED,
                "FAILED", RunTimelineEventData.failure(RunFailureCode.INSUFFICIENT_EVIDENCE.name()));

        RunDTO dto = RunAssembler.toDTO(run, List.of(), approval, List.of(retrieval, modelCall, failure));

        assertEquals("Summarize api_key=[REDACTED]\nCookie: [REDACTED]", dto.getGoal());
        assertTrue(dto.getApproval().getArguments().contains("\"title\":\"Review plan\""));
        assertTrue(dto.getApproval().getArguments().contains("\"body\":\"See [S1]\""));
        assertTrue(dto.getApproval().getArguments().contains("\"api_key\":\"[REDACTED]\""));
        assertFalse(dto.getApproval().getArguments().contains(SENTINEL));
        assertFalse(dto.toString().contains(COOKIE_SENTINEL));
        assertFalse(dto.getApproval().getArguments().contains("agentSentinelabcdefghijklmnopqrstuvwxyz"));
        assertFalse(dto.getApproval().getArguments().contains("sessionSentinelabcdefghijklmnopqrstuvwxyz"));
        assertFalse(dto.getResult().contains(COOKIE_SENTINEL));
        assertTrue(approval.getArguments().contains(SENTINEL),
                "owner preview redaction must preserve execution snapshot");
        assertEquals("ADD_OR_SELECT_EVIDENCE", dto.getNextAction());
        assertEquals(1, dto.getModelUsage().modelCallCount());
        assertEquals(13, dto.getModelUsage().inputTokens());
        assertEquals(5, dto.getModelUsage().outputTokens());
        assertTrue(dto.getModelUsage().complete());
        assertEquals(List.of("KNOWLEDGE_SOURCE", "MODEL_CALL", "RUN_FAILED"),
                dto.getTimeline().stream().map(event -> event.eventType()).toList());
        assertTrue(dto.getTimeline().stream()
                .noneMatch(event -> event.details().toString().contains(SENTINEL)
                        || event.details().toString().contains(COOKIE_SENTINEL)
                        || event.details().toString().contains("agentSentinelabcdefghijklmnopqrstuvwxyz")));
    }

    @Test
    void shouldMarkAggregatedUsageIncompleteWhenProviderOmitsTokenCounts() {
        RunEntity run = RunEntity.create("owner", "session", "agent", "goal");
        run.setStatus("RUNNING");
        RunTimelineEventEntity available = RunTimelineEventEntity.create("run", "step", RunTimelineEventType.MODEL_CALL,
                "COMPLETED", RunTimelineEventData.modelUsage("safe-model", 4, 2, true, 1));
        RunTimelineEventEntity missing = RunTimelineEventEntity.create("run", "step", RunTimelineEventType.MODEL_CALL,
                "COMPLETED", RunTimelineEventData.modelUsage("safe-model", null, null, false, 1));

        RunDTO dto = RunAssembler.toDTO(run, List.of(), null, List.of(available, missing));

        assertEquals(2, dto.getModelUsage().modelCallCount());
        assertEquals(4, dto.getModelUsage().inputTokens());
        assertEquals(2, dto.getModelUsage().outputTokens());
        assertFalse(dto.getModelUsage().complete());
        assertEquals("WAIT", dto.getNextAction());
    }

    @Test
    void shouldRedactJsonCredentialSentinelsFromOwnerFacingRunTextAndPreserveSafeFields() {
        RunEntity run = RunEntity.create("owner", "session", "agent", "Summarize the release");
        run.setStatus("FAILED");
        run.setGoal("Review: {\"api_key\":\"dtoJsonSentinel1234567890\","
                + "\"title\":\"Keep this title\",\"body\":\"Citation [S1]\"");
        run.setResult("Failure evidence: {\"authorization\":\"dtoAuthSentinel1234567890\"");

        RunDTO dto = RunAssembler.toDTO(run, List.of());

        assertFalse(dto.getGoal().contains("dtoJsonSentinel1234567890"));
        assertFalse(dto.getResult().contains("dtoAuthSentinel1234567890"));
        assertTrue(dto.getGoal().contains("Keep this title"));
        assertTrue(dto.getGoal().contains("Citation [S1]"));
        assertTrue(dto.getGoal().contains("[REDACTED]"));
    }
}
