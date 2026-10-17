package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunApprovalEntity;

/** Validates external resource identifiers and evidence references for the owning connector. */
public interface ApprovedActionReferenceValidator {
    boolean isValidExternalResourceId(String value, String targetRepository);

    boolean isValidEvidenceReference(String value, RunApprovalEntity approval);
}
