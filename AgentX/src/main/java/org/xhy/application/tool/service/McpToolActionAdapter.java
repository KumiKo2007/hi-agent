package org.xhy.application.tool.service;

import org.xhy.application.tool.model.RegisteredToolAction;

import java.util.Map;

/** Optional connector-owned behavior for exposing and freezing an MCP action. */
public interface McpToolActionAdapter {
    boolean supports(RegisteredToolAction action);

    default boolean isVisible(RegisteredToolAction action, String selectedTargetRepository) {
        return true;
    }

    default String resolveTargetRepository(RegisteredToolAction action, String arguments) {
        return null;
    }

    default String prepareApprovalArguments(RegisteredToolAction action, Map<String, Object> schema,
            String validatedArguments, String intentId) {
        return validatedArguments;
    }
}
