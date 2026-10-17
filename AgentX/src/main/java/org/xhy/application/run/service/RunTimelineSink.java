package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunTimelineEventEntity;

/** Per-run event callback used while executing model and tool calls. */
@FunctionalInterface
public interface RunTimelineSink {
    void append(RunTimelineEventEntity event);

    static RunTimelineSink noop() {
        return event -> { };
    }
}
