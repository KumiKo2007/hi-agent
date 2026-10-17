package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;

/** Executes only the fixed action stored on an approved run request. */
@FunctionalInterface
public interface ApprovedRunActionExecutor {
    String execute(RunEntity run, RunApprovalEntity approval);
}
