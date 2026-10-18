package org.xhy.domain.run.repository;

import org.xhy.domain.run.model.RunTimelineEventEntity;

import java.util.List;

/** Append-only persistence boundary for sanitized run events. */
public interface RunTimelineRepository {
    void append(RunTimelineEventEntity event);

    List<RunTimelineEventEntity> eventsForRun(String runId);
}
