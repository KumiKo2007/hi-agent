package org.xhy.application.run.service;

/** Result of checking an uncertain write against authoritative external evidence. */
public record ApprovedActionReconciliation(Outcome outcome, String externalResourceId, String evidence) {
    public enum Outcome {
        VERIFIED_CREATED, VERIFIED_NOT_CREATED, UNKNOWN
    }

    public static ApprovedActionReconciliation created(String resourceId, String evidence) {
        return new ApprovedActionReconciliation(Outcome.VERIFIED_CREATED, resourceId, evidence);
    }

    public static ApprovedActionReconciliation notCreated(String evidence) {
        return new ApprovedActionReconciliation(Outcome.VERIFIED_NOT_CREATED, null, evidence);
    }

    public static ApprovedActionReconciliation unknown(String reason) {
        return new ApprovedActionReconciliation(Outcome.UNKNOWN, null, reason);
    }
}
