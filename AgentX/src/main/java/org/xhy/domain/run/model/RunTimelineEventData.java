package org.xhy.domain.run.model;

/** Typed, allowlisted metadata for one timeline event. It deliberately has no prompt/argument/output fields. */
public record RunTimelineEventData(String failureCode, String modelName, Integer inputTokens, Integer outputTokens,
        String usageStatus, Long durationMs, String knowledgeVersionId, String sourceDocumentId, String sourceFileId,
        String sourceName, Integer sourcePage, String sourceUrl, String serviceId, String actionName,
        String targetRepository, String actorId, String decision, String resultReference) {

    public static RunTimelineEventData empty() {
        return new RunTimelineEventData(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static RunTimelineEventData failure(String failureCode) {
        return new RunTimelineEventData(failureCode, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    public static RunTimelineEventData retrieval(String knowledgeVersionId) {
        return new RunTimelineEventData(null, null, null, null, null, null, knowledgeVersionId, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static RunTimelineEventData source(String knowledgeVersionId, String documentId, String fileId,
            String fileName, Integer page, String sourceUrl) {
        return new RunTimelineEventData(null, null, null, null, null, null, knowledgeVersionId, documentId, fileId,
                fileName, page, sourceUrl, null, null, null, null, null, null);
    }

    public static RunTimelineEventData modelUsage(String modelName, Integer inputTokens, Integer outputTokens,
            boolean usageAvailable, long durationMs) {
        return new RunTimelineEventData(null, modelName, inputTokens, outputTokens,
                usageAvailable ? "AVAILABLE" : "UNAVAILABLE", durationMs, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static RunTimelineEventData action(String serviceId, String actionName, String targetRepository,
            String resultReference, String failureCode) {
        return new RunTimelineEventData(failureCode, null, null, null, null, null, null, null, null, null, null, null,
                serviceId, actionName, targetRepository, null, null, resultReference);
    }

    public static RunTimelineEventData approval(String serviceId, String actionName, String targetRepository,
            String actorId, String decision) {
        return new RunTimelineEventData(null, null, null, null, null, null, null, null, null, null, null, null,
                serviceId, actionName, targetRepository, actorId, decision, null);
    }

    public static RunTimelineEventData reconciliation(String serviceId, String actionName, String targetRepository,
            String outcome, String actorId) {
        return new RunTimelineEventData(null, null, null, null, null, null, null, null, null, null, null, null,
                serviceId, actionName, targetRepository, actorId, outcome, null);
    }
}
