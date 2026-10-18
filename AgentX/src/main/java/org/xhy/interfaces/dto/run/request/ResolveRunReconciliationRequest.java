package org.xhy.interfaces.dto.run.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Evidence-backed owner resolution for one uncertain approved action. */
public class ResolveRunReconciliationRequest {
    @NotNull
    private Outcome outcome;
    @NotBlank
    @Size(max = 2000)
    private String evidence;
    @Size(max = 512)
    private String evidenceReference;
    @Size(max = 512)
    private String externalResourceId;

    public Outcome getOutcome() {
        return outcome;
    }
    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }
    public String getEvidence() {
        return evidence;
    }
    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }
    public String getEvidenceReference() {
        return evidenceReference;
    }
    public void setEvidenceReference(String evidenceReference) {
        this.evidenceReference = evidenceReference;
    }
    public String getExternalResourceId() {
        return externalResourceId;
    }
    public void setExternalResourceId(String externalResourceId) {
        this.externalResourceId = externalResourceId;
    }

    public enum Outcome {
        RECORD_CREATED, CONFIRM_NOT_CREATED, STOP
    }
}
