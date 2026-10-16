package org.xhy.application.run.service;

import java.util.List;

/** Public result of querying one pinned knowledge snapshot. */
public class KnowledgeRetrievalResult {
    private final String knowledgeVersionId;
    private final boolean hasEvidence;
    private final String message;
    private final List<KnowledgeCitation> citations;

    public KnowledgeRetrievalResult(String knowledgeVersionId, boolean hasEvidence, String message,
            List<KnowledgeCitation> citations) {
        this.knowledgeVersionId = knowledgeVersionId;
        this.hasEvidence = hasEvidence;
        this.message = message;
        this.citations = List.copyOf(citations);
    }

    public String getKnowledgeVersionId() {
        return knowledgeVersionId;
    }

    public boolean hasEvidence() {
        return hasEvidence;
    }

    public boolean isHasEvidence() {
        return hasEvidence;
    }

    public String getMessage() {
        return message;
    }

    public List<KnowledgeCitation> getCitations() {
        return citations;
    }

    /** A draft without evidence cannot be automatically published. */
    public boolean canPublish() {
        return hasEvidence && !citations.isEmpty();
    }

    /** JavaBean form used by Jackson in the public HTTP response. */
    public boolean isCanPublish() {
        return canPublish();
    }
}
