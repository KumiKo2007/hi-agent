package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunApprovalDraft;

/** A worker outcome: a completed answer or a write action that needs owner approval. */
public record RunExecution(String answer, RunApprovalDraft approvalRequest, boolean preauthorized) {
    public static RunExecution completed(String answer) {
        return new RunExecution(answer, null, false);
    }

    public static RunExecution awaitingApproval(RunApprovalDraft request) {
        return new RunExecution(null, request, false);
    }

    public static RunExecution preauthorizedWrite(RunApprovalDraft request) {
        return new RunExecution(null, request, true);
    }

    public boolean isAwaitingApproval() {
        return approvalRequest != null && !preauthorized;
    }
}
