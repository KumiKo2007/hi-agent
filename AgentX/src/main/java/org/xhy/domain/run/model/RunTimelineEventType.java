package org.xhy.domain.run.model;

/** Allowlisted event types persisted for an owner-visible run timeline. */
public enum RunTimelineEventType {
    RUN_CREATED, RUN_ATTEMPT_STARTED, KNOWLEDGE_RETRIEVAL, KNOWLEDGE_SOURCE, MODEL_CALL, TOOL_ACTION, APPROVAL_REQUESTED, APPROVAL_DECISION, RECONCILIATION, TOOL_SCHEMA_REVIEW, RUN_COMPLETED, RUN_FAILED, RUN_CANCELLED
}
