package org.xhy.domain.run.model;

import org.xhy.infrastructure.exception.BusinessException;

/** Approval policy selected for a single run. Users can only make creator policy stricter. */
public enum RunApprovalPolicy {
    CREATOR_DEFAULT,
    REQUIRE_APPROVAL_FOR_ALL_WRITES;

    public static RunApprovalPolicy fromValue(String value) {
        if (value == null || value.isBlank()) {
            return CREATOR_DEFAULT;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("运行审批策略无效");
        }
    }

    public boolean requiresApproval(boolean creatorPreauthorized) {
        return this == REQUIRE_APPROVAL_FOR_ALL_WRITES || !creatorPreauthorized;
    }
}
