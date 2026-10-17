package org.xhy.application.github.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubApprovedActionReferenceValidatorTest {
    private final GitHubApprovedActionReferenceValidator validator = new GitHubApprovedActionReferenceValidator();
    private final RunApprovalEntity issueApproval = RunApprovalEntity.create("run-1", new RunApprovalDraft("github",
            "create_issue", "Create issue", "octo/project", "{}", Map.of("type", "object")));

    @Test
    void acceptsOnlySameRepositoryIssueLinksAndSupportedOpaqueIdentifiers() {
        assertTrue(validator.isValidExternalResourceId("https://github.com/octo/project/issues/42", "octo/project"));
        assertTrue(validator.isValidExternalResourceId("issue:42", "octo/project"));
        assertFalse(
                validator.isValidExternalResourceId("https://github.com/elsewhere/private/issues/42", "octo/project"));
        assertFalse(validator.isValidExternalResourceId("https://github.com/octo/project/pull/42", "octo/project"));
    }

    @Test
    void acceptsSameRepositoryIssueEvidenceButRejectsForeignAndPullRequestLinks() {
        assertTrue(validator.isValidEvidenceReference("https://github.com/octo/project/issues/7", issueApproval));
        assertTrue(validator.isValidEvidenceReference("audit:record-7", issueApproval));
        assertFalse(validator.isValidEvidenceReference("https://github.com/elsewhere/private/issues/7", issueApproval));
        assertFalse(validator.isValidEvidenceReference("https://github.com/octo/project/pull/7", issueApproval));
    }
}
