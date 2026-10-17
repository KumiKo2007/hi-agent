package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunFailureCode;
import org.xhy.domain.run.model.RunTimelineEventData;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.run.model.RunTimelineEventType;
import java.util.regex.Pattern;

/** Factories for safe timeline events; event summaries and payloads are always allowlisted. */
public final class RunTimelineEvents {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:#/-]{0,199}");
    private static final Pattern GITHUB_ISSUE = Pattern.compile(
            "https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/issues/[1-9][0-9]*");

    private RunTimelineEvents() {
    }

    public static RunTimelineEventEntity runCreated(String runId, String stepId) {
        return event(runId, stepId, RunTimelineEventType.RUN_CREATED, "PENDING", RunTimelineEventData.empty());
    }

    public static RunTimelineEventEntity attemptStarted(String runId, String stepId) {
        return event(runId, stepId, RunTimelineEventType.RUN_ATTEMPT_STARTED, "RUNNING",
                RunTimelineEventData.empty());
    }

    public static RunTimelineEventEntity runCompleted(String runId, String stepId) {
        return event(runId, stepId, RunTimelineEventType.RUN_COMPLETED, "COMPLETED", RunTimelineEventData.empty());
    }

    public static RunTimelineEventEntity runFailed(String runId, String stepId, RunFailureCode code) {
        return event(runId, stepId, RunTimelineEventType.RUN_FAILED, "FAILED",
                RunTimelineEventData.failure(code.name()));
    }

    public static RunTimelineEventEntity runCancelled(String runId, String stepId) {
        return event(runId, stepId, RunTimelineEventType.RUN_CANCELLED, "CANCELLED", RunTimelineEventData.empty());
    }

    public static RunTimelineEventEntity knowledgeRetrieval(String runId, String stepId, String versionId,
            boolean sufficient) {
        return event(runId, stepId, RunTimelineEventType.KNOWLEDGE_RETRIEVAL,
                sufficient ? "COMPLETED" : "INSUFFICIENT", RunTimelineEventData.retrieval(safeIdentifier(versionId)));
    }

    public static RunTimelineEventEntity knowledgeRetrievalFailed(String runId, String stepId, String versionId,
            RunFailureCode failureCode) {
        return event(runId, stepId, RunTimelineEventType.KNOWLEDGE_RETRIEVAL, "FAILED",
                new RunTimelineEventData(failureCode.name(), null, null, null, null, null,
                        safeIdentifier(versionId), null, null, null, null, null, null, null, null,
                        null, null, null));
    }

    public static RunTimelineEventEntity knowledgeSource(String runId, String stepId, KnowledgeCitation citation) {
        return event(runId, stepId, RunTimelineEventType.KNOWLEDGE_SOURCE, "USED",
                RunTimelineEventData.source(safeIdentifier(citation.getKnowledgeVersionId()),
                        safeIdentifier(citation.getDocumentId()), safeIdentifier(citation.getFileId()),
                        safeLabel(citation.getFileName(), 255), citation.getPage(), safeSourceUrl(citation.getSourceUrl())));
    }

    public static RunTimelineEventEntity modelCall(String runId, String stepId, String modelName,
            Integer inputTokens, Integer outputTokens, boolean usageAvailable, long durationMs, boolean success) {
        Integer safeInput = nonNegative(inputTokens);
        Integer safeOutput = nonNegative(outputTokens);
        boolean completeUsage = usageAvailable && safeInput != null && safeOutput != null;
        return event(runId, stepId, RunTimelineEventType.MODEL_CALL, success ? "COMPLETED" : "FAILED",
                RunTimelineEventData.modelUsage(safeLabel(modelName, 128), completeUsage ? safeInput : null,
                        completeUsage ? safeOutput : null, completeUsage, Math.max(0L, durationMs)));
    }

    public static RunTimelineEventEntity toolAction(String runId, String stepId, String serviceId, String actionName,
            String repository, String status, String resultReference, RunFailureCode failureCode) {
        return event(runId, stepId, RunTimelineEventType.TOOL_ACTION, status,
                RunTimelineEventData.action(safeIdentifier(serviceId), safeLabel(actionName, 255),
                        safeLabel(repository, 200), safeExternalReference(resultReference),
                        failureCode == null ? null : failureCode.name()));
    }

    public static RunTimelineEventEntity approvalRequested(String runId, String stepId, RunApprovalEntity approval) {
        return event(runId, stepId, RunTimelineEventType.APPROVAL_REQUESTED, "PENDING",
                RunTimelineEventData.action(safeIdentifier(approval.getServiceId()), safeLabel(approval.getActionName(), 255),
                        safeLabel(approval.getTargetRepository(), 200), null, null));
    }

    public static RunTimelineEventEntity approvalDecision(String runId, String stepId, RunApprovalEntity approval) {
        boolean preauthorized = "CREATOR_PREAUTHORIZATION".equals(approval.getApprovalSource());
        String decision = preauthorized ? "PREAUTHORIZED" : approval.getStatus();
        RunTimelineEventEntity event = event(runId, stepId, RunTimelineEventType.APPROVAL_DECISION, decision,
                RunTimelineEventData.approval(safeIdentifier(approval.getServiceId()),
                        safeLabel(approval.getActionName(), 255), safeLabel(approval.getTargetRepository(), 200),
                        safeIdentifier(approval.getDecidedBy()), decision));
        if (approval.getDecidedAt() != null) {
            event.setCreatedAt(approval.getDecidedAt());
            event.setUpdatedAt(approval.getDecidedAt());
        }
        return event;
    }

    public static RunTimelineEventEntity reconciliation(String runId, String stepId, RunApprovalEntity approval,
            String outcome, String actorId) {
        String safeOutcome = switch (outcome == null ? "" : outcome) {
            case "VERIFIED_CREATED", "RECORD_CREATED" -> "VERIFIED_CREATED";
            case "VERIFIED_NOT_CREATED", "CONFIRM_NOT_CREATED" -> "VERIFIED_NOT_CREATED";
            case "STOP" -> "STOPPED";
            default -> "UNKNOWN";
        };
        return event(runId, stepId, RunTimelineEventType.RECONCILIATION, safeOutcome,
                RunTimelineEventData.reconciliation(safeIdentifier(approval.getServiceId()),
                        safeLabel(approval.getActionName(), 255), safeLabel(approval.getTargetRepository(), 200),
                        safeOutcome, safeIdentifier(actorId)));
    }

    public static RunTimelineEventEntity schemaReview(String runId, String stepId) {
        return event(runId, stepId, RunTimelineEventType.TOOL_SCHEMA_REVIEW, "WAITING",
                RunTimelineEventData.failure(RunFailureCode.TOOL_SCHEMA_CHANGED.name()));
    }

    public static RunTimelineEventEntity failure(String runId, String stepId, RunFailureCode code) {
        return runFailed(runId, stepId, code);
    }

    private static RunTimelineEventEntity event(String runId, String stepId, RunTimelineEventType type,
            String status, RunTimelineEventData data) {
        return RunTimelineEventEntity.create(runId, stepId, type, status, data);
    }

    private static Integer nonNegative(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String safe = RunTimelineSanitizer.redactText(value.trim());
        return SAFE_IDENTIFIER.matcher(safe).matches() ? safe : null;
    }

    private static String safeLabel(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String safe = RunTimelineSanitizer.redactText(value.trim()).replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(safe.length(), maximumLength));
    }

    private static String safeSourceUrl(String value) {
        if (value == null || !value.startsWith("/rag/datasets/")) {
            return null;
        }
        return safeLabel(value, 512);
    }

    private static String safeExternalReference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String safe = RunTimelineSanitizer.redactText(value.trim());
        if (GITHUB_ISSUE.matcher(safe).matches()) {
            return safe;
        }
        return SAFE_IDENTIFIER.matcher(safe).matches() ? safe : null;
    }
}
