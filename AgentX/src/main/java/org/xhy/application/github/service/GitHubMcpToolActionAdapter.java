package org.xhy.application.github.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.service.McpSchema;
import org.xhy.application.tool.service.McpToolActionAdapter;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.Map;

/** GitHub action discovery and approval metadata kept outside the generic MCP broker. */
@Component
public class GitHubMcpToolActionAdapter implements McpToolActionAdapter {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public boolean supports(RegisteredToolAction action) {
        return action != null && "github".equalsIgnoreCase(action.serverName());
    }

    @Override
    public boolean isVisible(RegisteredToolAction action, String selectedTargetRepository) {
        return selectedTargetRepository != null && !selectedTargetRepository.isBlank();
    }

    @Override
    public String resolveTargetRepository(RegisteredToolAction action, String arguments) {
        return McpSchema.targetRepository(arguments);
    }

    @Override
    public String prepareApprovalArguments(RegisteredToolAction action, Map<String, Object> schema,
            String validatedArguments, String intentId) {
        if (!"create_issue".equals(action.name()) || !hasStringBody(schema)) {
            return validatedArguments;
        }
        try {
            JsonNode parsed = JSON.readTree(validatedArguments);
            if (!(parsed instanceof ObjectNode object)) {
                throw new BusinessException("无法固定 GitHub Issue 写入标记");
            }
            String marker = "<!-- hi-agent-action-intent:" + intentId + " -->";
            String existingBody = object.path("body").isTextual() ? object.path("body").asText() : "";
            object.put("body", existingBody.isBlank() ? marker : existingBody + "\n\n" + marker);
            return McpSchema.validateArguments(schema, JSON.writeValueAsString(object));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("无法固定 GitHub Issue 写入标记");
        }
    }

    private boolean hasStringBody(Map<String, Object> schema) {
        Object propertiesValue = schema.get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)) {
            return false;
        }
        Object bodyValue = properties.get("body");
        return bodyValue instanceof Map<?, ?> body && "string".equals(body.get("type"));
    }
}
