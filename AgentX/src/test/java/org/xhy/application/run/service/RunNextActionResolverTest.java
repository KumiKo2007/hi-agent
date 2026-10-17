package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunNextActionResolverTest {

    @Test
    void mapsWaitingStatesAndTerminalFailuresToConcreteOwnerActions() {
        assertEquals(RunNextAction.OWNER_DECISION, RunNextActionResolver.resolve("WAITING_APPROVAL", null));
        assertEquals(RunNextAction.REVIEW_TOOL_SCHEMA,
                RunNextActionResolver.resolve("WAITING_SCHEMA_REVIEW", null));
        assertEquals(RunNextAction.RECONCILE_EXTERNAL_WRITE,
                RunNextActionResolver.resolve("WAITING_RECONCILIATION", null));
        assertEquals(RunNextAction.ADD_OR_SELECT_EVIDENCE,
                RunNextActionResolver.resolve("FAILED", "INSUFFICIENT_EVIDENCE"));
        assertEquals(RunNextAction.CHECK_REPOSITORY_AUTHORIZATION,
                RunNextActionResolver.resolve("FAILED", "PERMISSION_DENIED"));
        assertEquals(RunNextAction.START_NEW_RUN, RunNextActionResolver.resolve("FAILED", "EXECUTION_FAILED"));
        assertEquals(RunNextAction.WAIT, RunNextActionResolver.resolve("RUNNING", null));
        assertEquals(RunNextAction.NONE, RunNextActionResolver.resolve("COMPLETED", null));
        assertEquals(RunNextAction.NONE, RunNextActionResolver.resolve("CANCELLED", null));
    }
}
