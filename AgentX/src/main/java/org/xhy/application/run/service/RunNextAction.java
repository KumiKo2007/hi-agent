package org.xhy.application.run.service;

/** Safe UI guidance derived from a run's persisted state. It never grants permission or performs an action. */
public enum RunNextAction {
    OWNER_DECISION, REVIEW_TOOL_SCHEMA, RECONCILE_EXTERNAL_WRITE, ADD_OR_SELECT_EVIDENCE, CHECK_REPOSITORY_AUTHORIZATION, CHECK_MODEL_CONNECTION, START_NEW_RUN, WAIT, NONE
}
