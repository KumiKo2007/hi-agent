package org.xhy.application.tool.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.xhy.infrastructure.exception.BusinessException;

/** 把 MCP 参数结构规范化，并拒绝不符合固定结构的模型参数。 */
public final class McpSchema {
    private static final ObjectMapper JSON = new ObjectMapper();

    private McpSchema() {
    }

    public static Map<String, Object> fromRuntime(JsonObjectSchema schema) {
        if (schema == null) {
            return Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties",
                    false);
        }
        return objectSchema(schema);
    }

    private static Map<String, Object> objectSchema(JsonObjectSchema schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (schema.properties() != null) {
            schema.properties().forEach((name, value) -> properties.put(name, element(value)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", properties);
        result.put("required", schema.required() == null ? List.of() : schema.required());
        result.put("additionalProperties", Boolean.TRUE.equals(schema.additionalProperties()));
        if (schema.definitions() != null && !schema.definitions().isEmpty()) {
            throw new BusinessException("暂不支持包含定义引用的工具参数结构");
        }
        return result;
    }

    private static Map<String, Object> element(JsonSchemaElement element) {
        if (element instanceof JsonObjectSchema object) {
            return objectSchema(object);
        }
        if (element instanceof JsonArraySchema array) {
            return Map.of("type", "array", "items", element(array.items()));
        }
        if (element instanceof JsonEnumSchema enumeration) {
            return Map.of("type", "string", "enum", enumeration.enumValues());
        }
        if (element instanceof JsonStringSchema) {
            return Map.of("type", "string");
        }
        if (element instanceof JsonIntegerSchema) {
            return Map.of("type", "integer");
        }
        if (element instanceof JsonNumberSchema) {
            return Map.of("type", "number");
        }
        if (element instanceof JsonBooleanSchema) {
            return Map.of("type", "boolean");
        }
        if (element instanceof JsonNullSchema) {
            return Map.of("type", "null");
        }
        if (element instanceof JsonAnyOfSchema || element instanceof JsonReferenceSchema) {
            throw new BusinessException("暂不支持组合或引用的工具参数结构");
        }
        throw new BusinessException("未知的工具参数结构");
    }

    public static String validateArguments(Map<String, Object> schema, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new BusinessException("工具参数不是有效 JSON");
        }
        try {
            Object value = JSON.readValue(arguments, Object.class);
            if (!matches(schema, value)) {
                throw new BusinessException("工具参数不符合平台登记的结构");
            }
            // MCP 只接收这一份已解析的参数，避免重复 JSON 键在两端产生不同解释。
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("工具参数不是有效 JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean matches(Map<String, Object> schema, Object value) {
        if (schema == null) {
            return false;
        }
        Object type = schema.get("type");
        if ("object".equals(type)) {
            if (!(value instanceof Map<?, ?> values) || !(schema.get("properties") instanceof Map<?, ?> properties)) {
                return false;
            }
            if (schema.get("required") instanceof List<?> required && !values.keySet().containsAll(required)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                Object property = properties.get(entry.getKey());
                if (!(property instanceof Map<?, ?> propertySchema)) {
                    return false;
                }
                if (!matches((Map<String, Object>) propertySchema, entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if ("array".equals(type)) {
            if (!(value instanceof List<?> values) || !(schema.get("items") instanceof Map<?, ?> itemSchema)) {
                return false;
            }
            return values.stream().allMatch(item -> matches((Map<String, Object>) itemSchema, item));
        }
        if ("string".equals(type)) {
            return value instanceof String
                    && (!(schema.get("enum") instanceof List<?> choices) || choices.contains(value));
        }
        if ("integer".equals(type)) {
            return value instanceof Integer || value instanceof Long;
        }
        if ("number".equals(type)) {
            return value instanceof Number;
        }
        if ("boolean".equals(type)) {
            return value instanceof Boolean;
        }
        return "null".equals(type) && value == null;
    }
}
