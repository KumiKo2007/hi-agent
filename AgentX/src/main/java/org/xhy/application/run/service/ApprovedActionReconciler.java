package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;

/** Performs read-only reconciliation; search misses must remain UNKNOWN. */
@FunctionalInterface
public interface ApprovedActionReconciler {
    ApprovedActionReconciliation reconcile(RunEntity run, RunApprovalEntity approval);
}
