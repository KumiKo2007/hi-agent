package org.xhy.application.tool.service;

import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpSchemaTest {
    @Test
    void honorsAdditionalPropertiesTrueWhenValidatingArguments() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"), "additionalProperties", true);

        assertDoesNotThrow(() -> McpSchema.validateArguments(schema, "{\"query\":\"release\",\"locale\":\"en\"}"));
    }

    @Test
    void rejectsUnknownPropertiesWhenAdditionalPropertiesIsFalse() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"), "additionalProperties", false);

        assertThrows(BusinessException.class,
                () -> McpSchema.validateArguments(schema, "{\"query\":\"release\",\"locale\":\"en\"}"));
    }
}
