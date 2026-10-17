package org.xhy.application.run.dto;

/** Aggregated model usage for the model calls observed by this run. */
public record RunModelUsageDTO(int modelCallCount, long inputTokens, long outputTokens, boolean complete) {
}
