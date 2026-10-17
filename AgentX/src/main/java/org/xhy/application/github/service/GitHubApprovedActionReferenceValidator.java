package org.xhy.application.github.service;

import org.springframework.stereotype.Component;
import org.xhy.application.run.service.ApprovedActionReferenceValidator;
import org.xhy.domain.run.model.RunApprovalEntity;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies GitHub Issue URL rules to owner-supplied reconciliation records. */
@Component
public class GitHubApprovedActionReferenceValidator implements ApprovedActionReferenceValidator {
    private static final Pattern GITHUB_ISSUE_URL = Pattern
            .compile("https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/issues/([1-9][0-9]*)/?");
    private static final Pattern CANONICAL_GITHUB_ISSUE_URL = Pattern
            .compile("https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/issues/([1-9][0-9]*)");
    private static final Pattern OPAQUE_EXTERNAL_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:#-]{0,199}");

    @Override
    public boolean isValidExternalResourceId(String value, String targetRepository) {
        if (value == null || targetRepository == null) {
            return false;
        }
        String trimmed = value.trim();
        if (OPAQUE_EXTERNAL_ID.matcher(trimmed).matches()) {
            return true;
        }
        Matcher matcher = GITHUB_ISSUE_URL.matcher(trimmed);
        return matcher.matches() && sameRepository(matcher, targetRepository);
    }

    @Override
    public boolean isValidEvidenceReference(String value, RunApprovalEntity approval) {
        if (value == null || approval == null) {
            return false;
        }
        if (OPAQUE_EXTERNAL_ID.matcher(value).matches()) {
            return true;
        }
        Matcher matcher = CANONICAL_GITHUB_ISSUE_URL.matcher(value);
        return matcher.matches() && "create_issue".equals(approval.getActionName())
                && sameRepository(matcher, approval.getTargetRepository());
    }

    private boolean sameRepository(Matcher matcher, String targetRepository) {
        if (targetRepository == null) {
            return false;
        }
        return (matcher.group(1) + "/" + matcher.group(2)).toLowerCase(Locale.ROOT)
                .equals(targetRepository.toLowerCase(Locale.ROOT));
    }
}
