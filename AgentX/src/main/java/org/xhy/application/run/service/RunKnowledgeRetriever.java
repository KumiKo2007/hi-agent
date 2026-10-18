package org.xhy.application.run.service;

/** Retrieves evidence from the immutable knowledge snapshot pinned to one run. */
@FunctionalInterface
public interface RunKnowledgeRetriever {
    KnowledgeRetrievalResult retrieve(String knowledgeVersionId, String query, String userId);
}
