package org.xhy.interfaces.dto.run.request;

import jakarta.validation.constraints.NotNull;

/** Owner's decision for one immutable run approval request. */
public class DecideRunApprovalRequest {
    @NotNull
    private Decision decision;

    public Decision getDecision() {
        return decision;
    }
    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public enum Decision {
        APPROVE, DENY
    }
}
