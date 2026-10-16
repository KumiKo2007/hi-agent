package org.xhy.application.run.service;

/** Resolves the immutable knowledge snapshot a run must use when it is created. */
@FunctionalInterface
public interface RunKnowledgeVersionResolver {

    /** @return the ready knowledge version ID to pin, or {@code null} when the agent has no ready knowledge */
    String resolve(String agentId, String userId);

    static RunKnowledgeVersionResolver none() {
        return (agentId, userId) -> null;
    }
}
