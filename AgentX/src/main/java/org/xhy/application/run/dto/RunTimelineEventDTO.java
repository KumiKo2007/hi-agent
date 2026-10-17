package org.xhy.application.run.dto;

import org.xhy.domain.run.model.RunTimelineEventData;

import java.time.LocalDateTime;

/** Explicit allowlist for a single owner-visible run event. */
public record RunTimelineEventDTO(String id, String runId, String stepId, String eventType, String status,
        String summary, RunTimelineEventData details, LocalDateTime createdAt) {
}
