package org.xhy.application.tool.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.modelcontextprotocol.spec.McpSchema;

/** Maps official MCP tool definitions onto the LangChain tool schema used by the broker. */
public final class McpSdkToolMapper {
    private static final Set<String> ANNOTATIONS = Set.of("title", "description", "$comment");

    private McpSdkToolMapper() {
    }

    public static ToolSpecification toToolSpecification(McpSchema.Tool tool) {
        return ToolSpecification.builder().name(tool.name()).description(tool.description())
                .parameters(toObjectSchema(tool.inputSchema())).build();
    }

    private static JsonObjectSchema toObjectSchema(Map<String, Object> schema) {
        if (schema == null || !"object".equals(schema.get("type"))) {
            throw paused();
        }
        requireOnlyKeywords(schema, Set.of("type", "properties", "required", "additionalProperties", "$schema"));
        if (schema.containsKey("$schema")
                && !"https://json-schema.org/draft/2020-12/schema".equals(schema.get("$schema"))) {
            throw paused();
        }
        if (schema.containsKey("properties") && !(schema.get("properties") instanceof Map<?, ?>)) {
            throw paused();
        }
        if (schema.containsKey("additionalProperties") && !(schema.get("additionalProperties") instanceof Boolean)) {
            throw paused();
        }
        if (schema.containsKey("required") && !(schema.get("required") instanceof List<?>)) {
            throw paused();
        }

        JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
                // JSON Schema permits additional properties by default; preserve that meaning for comparison.
                .additionalProperties(!Boolean.FALSE.equals(schema.get("additionalProperties")));
        Object rawProperties = schema.get("properties");
        if (rawProperties instanceof Map<?, ?> properties) {
            Map<String, JsonSchemaElement> mapped = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (!(entry.getKey() instanceof String name)) {
                    throw paused();
                }
                mapped.put(name, toElement(asMap(entry.getValue())));
            }
            builder.addProperties(mapped);
        }
        builder.required(schema.containsKey("required") ? stringList(schema.get("required")) : List.of());
        return builder.build();
    }

    private static JsonSchemaElement toElement(Map<String, Object> schema) {
        String type = schema.get("type") instanceof String value ? value : "";
        if (schema.containsKey("enum")) {
            requireOnlyKeywords(schema, Set.of("type", "enum"));
            if (!type.isEmpty() && !"string".equals(type)) {
                throw paused();
            }
            Object enumValue = schema.get("enum");
            if (!(enumValue instanceof List<?> values)) {
                throw paused();
            }
            List<String> strings = values.stream().map(value -> {
                if (!(value instanceof String string)) {
                    throw paused();
                }
                return string;
            }).toList();
            return JsonEnumSchema.builder().enumValues(strings).build();
        }

        return switch (type) {
            case "object" -> toObjectSchema(schema);
            case "array" -> {
                requireOnlyKeywords(schema, Set.of("type", "items"));
                yield JsonArraySchema.builder().items(toElement(asMap(schema.get("items")))).build();
            }
            case "string" -> {
                requireOnlyKeywords(schema, Set.of("type"));
                yield JsonStringSchema.builder().build();
            }
            case "integer" -> {
                requireOnlyKeywords(schema, Set.of("type"));
                yield JsonIntegerSchema.builder().build();
            }
            case "number" -> {
                requireOnlyKeywords(schema, Set.of("type"));
                yield JsonNumberSchema.builder().build();
            }
            case "boolean" -> {
                requireOnlyKeywords(schema, Set.of("type"));
                yield JsonBooleanSchema.builder().build();
            }
            case "null" -> {
                requireOnlyKeywords(schema, Set.of("type"));
                yield new JsonNullSchema();
            }
            default -> throw paused();
        };
    }

    private static void requireOnlyKeywords(Map<String, Object> schema, Set<String> supported) {
        for (String keyword : schema.keySet()) {
            if (!supported.contains(keyword) && !ANNOTATIONS.contains(keyword)) {
                throw paused();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw paused();
        }
        return (Map<String, Object>) value;
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw paused();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String string)) {
                throw paused();
            }
            result.add(string);
        }
        return result;
    }

    private static McpSchemaMismatchException paused() {
        return new McpSchemaMismatchException();
    }
}
