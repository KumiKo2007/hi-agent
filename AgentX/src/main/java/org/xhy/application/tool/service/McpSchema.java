package org.xhy.application.tool.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.net.URI;
import java.util.Locale;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;

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
        result.put("additionalProperties", !Boolean.FALSE.equals(schema.additionalProperties()));
        if (schema.definitions() != null && !schema.definitions().isEmpty()) {
            throw new McpSchemaMismatchException();
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
            throw new McpSchemaMismatchException();
        }
        throw new McpSchemaMismatchException();
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

    /** Finds the single GitHub repository this write targets and returns its canonical name. */
    public static String targetRepository(String arguments) {
        try {
            JsonNode root = JSON.readTree(arguments);
            if (root == null || !root.isObject()) {
                throw new BusinessException("写入动作必须指定目标 GitHub 仓库");
            }
            String owner = text(root, "owner");
            List<String> candidates = new ArrayList<>();
            String repository = text(root, "repository");
            if (repository != null) {
                candidates.add(withOwnerIfNeeded(owner, repository));
            }
            String name = text(root, "repo");
            if (name != null) {
                candidates.add(withOwnerIfNeeded(owner, name));
            }
            String repositoryUrl = text(root, "repository_url");
            if (repositoryUrl != null) {
                candidates.add(withOwnerIfNeeded(owner, repositoryUrl));
            }
            if (candidates.isEmpty()) {
                throw new BusinessException("写入动作必须指定目标 GitHub 仓库");
            }
            String target = normalizeRepository(candidates.get(0));
            if (candidates.stream().skip(1).map(McpSchema::normalizeRepository)
                    .anyMatch(value -> !target.equals(value))) {
                throw new BusinessException("写入动作指定了多个目标仓库");
            }
            return target;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("写入动作必须指定目标 GitHub 仓库");
        }
    }

    private static String withOwnerIfNeeded(String owner, String repository) {
        if (repository.startsWith("https://") || repository.startsWith("http://")) {
            String normalized = normalizeRepository(repository);
            if (owner != null && !owner.equalsIgnoreCase(normalized.substring(0, normalized.indexOf('/')))) {
                throw new BusinessException("写入动作指定了不一致的仓库所有者");
            }
            return normalized;
        }
        if (repository.contains("/")) {
            if (owner != null && !owner.equalsIgnoreCase(repository.substring(0, repository.indexOf('/')))) {
                throw new BusinessException("写入动作指定了不一致的仓库所有者");
            }
            return repository;
        }
        if (owner == null) {
            throw new BusinessException("写入动作必须指定 owner/repository 目标");
        }
        return owner + "/" + repository;
    }

    public static boolean sameSchema(String schemaJson, Map<String, Object> schema) {
        try {
            return JSON.readTree(schemaJson).equals(JSON.valueToTree(schema));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean sameSchema(Map<String, Object> left, Map<String, Object> right) {
        try {
            return JSON.valueToTree(left).equals(JSON.valueToTree(right));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static Map<String, Object> readSnapshot(String schemaJson) {
        try {
            Map<String, Object> schema = JSON.readValue(schemaJson, new TypeReference<>() {
            });
            return snapshotSchema(schema);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new McpSchemaMismatchException();
        }
    }

    /** Copies nested schema maps and lists so a model-visible schema cannot change after registration. */
    public static Map<String, Object> snapshotSchema(Map<String, Object> schema) {
        if (schema == null) {
            throw new McpSchemaMismatchException();
        }
        return immutableMap(schema);
    }

    private static Map<String, Object> immutableMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name)) {
                throw new McpSchemaMismatchException();
            }
            copy.put(name, immutableValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap(map);
        }
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(list.stream().map(McpSchema::immutableValue).toList());
        }
        return value;
    }

    private static String text(JsonNode root, String key) {
        JsonNode value = root.get(key);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private static String normalizeRepository(String repository) {
        if (repository == null || repository.isBlank()) {
            throw new BusinessException("写入动作必须指定目标 GitHub 仓库");
        }
        String value = repository.trim();
        if (value.startsWith("https://") || value.startsWith("http://")) {
            try {
                URI uri = URI.create(value);
                if (!"github.com".equalsIgnoreCase(uri.getHost()) || uri.getQuery() != null
                        || uri.getFragment() != null) {
                    throw new BusinessException("写入动作必须指定 GitHub 仓库名称");
                }
                value = uri.getPath();
                if (value == null) {
                    throw new BusinessException("写入动作必须指定 GitHub 仓库名称");
                }
                value = value.replaceAll("^/|/$", "");
                if (value.endsWith(".git")) {
                    value = value.substring(0, value.length() - 4);
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("写入动作必须指定 GitHub 仓库名称");
            }
        }
        if (!value.matches("[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}")) {
            throw new BusinessException("写入动作必须指定 owner/repository 目标");
        }
        return value.toLowerCase(Locale.ROOT);
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
                boolean declared = properties.containsKey(entry.getKey());
                if (!declared && (Boolean.TRUE.equals(schema.get("additionalProperties"))
                        || !schema.containsKey("additionalProperties"))) {
                    continue;
                }
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
