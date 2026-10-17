package org.xhy.application.run.service;

/** Maps safe run status/failure codes to a bounded user-facing next action. */
public final class RunNextActionResolver {
    private RunNextActionResolver() {
    }

    public static RunNextAction resolve(String runStatus, String failureCode) {
        if ("WAITING_APPROVAL".equals(runStatus)) {
            return RunNextAction.OWNER_DECISION;
        }
        if ("WAITING_SCHEMA_REVIEW".equals(runStatus)) {
            return RunNextAction.REVIEW_TOOL_SCHEMA;
        }
        if ("WAITING_RECONCILIATION".equals(runStatus)) {
            return RunNextAction.RECONCILE_EXTERNAL_WRITE;
        }
        if ("PENDING".equals(runStatus) || "RUNNING".equals(runStatus)) {
            return RunNextAction.WAIT;
        }
        if ("COMPLETED".equals(runStatus) || "CANCELLED".equals(runStatus)) {
            return RunNextAction.NONE;
        }
        if ("FAILED".equals(runStatus)) {
            if ("INSUFFICIENT_EVIDENCE".equals(failureCode)) {
                return RunNextAction.ADD_OR_SELECT_EVIDENCE;
            }
            if ("PERMISSION_DENIED".equals(failureCode) || "TARGET_REPOSITORY_NOT_AUTHORIZED".equals(failureCode)) {
                return RunNextAction.CHECK_REPOSITORY_AUTHORIZATION;
            }
            if ("MODEL_UNAVAILABLE".equals(failureCode)) {
                return RunNextAction.CHECK_MODEL_CONNECTION;
            }
            return RunNextAction.START_NEW_RUN;
        }
        return RunNextAction.NONE;
    }
}
