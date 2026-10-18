package org.xhy.domain.run.model;

import java.util.Map;
import java.util.UUID;

/** Validated, fixed MCP write parameters proposed by a run model. */
public record RunApprovalDraft(String serviceId, String actionName, String actionDescription, String targetRepository,
        String arguments, Map<String, Object> schema, String intentId) {
    public RunApprovalDraft(String serviceId, String actionName, String actionDescription, String targetRepository,
            String arguments, Map<String, Object> schema) {
        this(serviceId, actionName, actionDescription, targetRepository, arguments, schema,
                UUID.randomUUID().toString());
    }

    public RunApprovalDraft {
        schema = schema == null ? Map.of() : Map.copyOf(schema);
        intentId = intentId == null || intentId.isBlank() ? UUID.randomUUID().toString() : intentId;
    }
}
