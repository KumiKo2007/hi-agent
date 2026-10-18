package org.xhy.application.run.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/** Removes credential-like values before run data is shown, persisted in a trace, or returned to a model. */
public final class RunTimelineSanitizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REDACTED = "[REDACTED]";
    private static final String HIDDEN_INVALID_ARGUMENTS = "Sensitive action details hidden (invalid JSON).";
    private static final Pattern BEARER_CREDENTIAL = Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern NAMED_CREDENTIAL = Pattern
            .compile("(?i)(authorization\\s*[:=]\\s*(?:(?:Bearer|Basic)\\s+)?|"
                    + "(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|"
                    + "password|passwd|credential|secret|private[_-]?key)\\s*[:=]\\s*)" + "([^\\s,;\\\"'<>]+)");
    private static final Pattern HEADER_BLOCK = Pattern
            .compile("(?i)(?<![\\\"'])((?:headers?|cookies?)\\s*[:=]\\s*)(?:\\{[^\\r\\n]*|[^\\r\\n]+)");
    private static final Pattern COOKIE_HEADER = Pattern
            .compile("(?im)(?<![\\\"'])(\\b(?:cookie|set-cookie)\\s*:\\s*)[^\\r\\n]+");
    private static final Pattern KNOWN_TOKEN = Pattern
            .compile("(?i)\\b(?:gh[pousr]_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{16,}|"
                    + "sk-[A-Za-z0-9_-]{16,}|xox[baprs]-[A-Za-z0-9-]{16,})\\b");

    private RunTimelineSanitizer() {
    }

    /** Redacts credential-named fields recursively; malformed JSON is hidden entirely. */
    public static String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = JSON.readTree(json);
            return JSON.writeValueAsString(redactNode(root));
        } catch (JsonProcessingException exception) {
            return HIDDEN_INVALID_ARGUMENTS;
        }
    }

    /** Redacts credential fields inside embedded JSON and common credential formats in plain text. */
    public static String redactText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return redactTextWithCredentialFields(text);
    }

    private static String redactTextWithCredentialFields(String text) {
        return redactPlainText(redactJsonCredentialFields(text));
    }

    /** Scans once for quoted JSON property names and redacts sensitive values in place. This also handles truncated
     * JSON, where there is no closing object for a parser or brace matcher to find. */
    private static String redactJsonCredentialFields(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int copyFrom = 0;
        int position = 0;
        while (position < text.length()) {
            if (text.charAt(position) != '"') {
                position++;
                continue;
            }

            int keyEnd = findQuotedStringEnd(text, position);
            if (keyEnd < 0) {
                break;
            }
            int colon = skipWhitespace(text, keyEnd + 1);
            if (colon < text.length() && text.charAt(colon) == ':') {
                String propertyName = readJsonString(text, position, keyEnd + 1);
                if (propertyName != null && isCredentialField(propertyName)) {
                    int valueStart = skipWhitespace(text, colon + 1);
                    int valueEnd = findSensitiveValueEnd(text, valueStart);
                    if (valueEnd > valueStart) {
                        result.append(text, copyFrom, valueStart);
                        result.append("\"[REDACTED]\"");
                        copyFrom = valueEnd;
                        position = valueEnd;
                        continue;
                    }
                }
            }
            position = keyEnd + 1;
        }
        result.append(text, copyFrom, text.length());
        return result.toString();
    }

    private static int findQuotedStringEnd(String text, int start) {
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    return index;
                }
            } else if (character == '"') {
                inString = true;
            }
        }
        return -1;
    }

    private static int findSensitiveValueEnd(String text, int start) {
        if (start >= text.length() || text.charAt(start) == ',' || text.charAt(start) == '}'
                || text.charAt(start) == ']') {
            return start;
        }
        char first = text.charAt(start);
        if (first == '"') {
            int stringEnd = findQuotedStringEnd(text, start);
            return stringEnd < 0 ? text.length() : stringEnd + 1;
        }
        if (first == '{' || first == '[') {
            return findCompoundValueEnd(text, start);
        }
        int end = start;
        while (end < text.length() && text.charAt(end) != ',' && text.charAt(end) != '}' && text.charAt(end) != ']'
                && text.charAt(end) != '\r' && text.charAt(end) != '\n') {
            end++;
        }
        return end;
    }

    private static int findCompoundValueEnd(String text, int start) {
        Deque<Character> expectedClosers = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{') {
                expectedClosers.push('}');
            } else if (character == '[') {
                expectedClosers.push(']');
            } else if (character == '}' || character == ']') {
                if (expectedClosers.isEmpty() || expectedClosers.peek() != character) {
                    return text.length();
                }
                expectedClosers.pop();
                if (expectedClosers.isEmpty()) {
                    return index + 1;
                }
            }
        }
        return text.length();
    }

    private static int skipWhitespace(String text, int start) {
        int position = start;
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }

    private static String readJsonString(String text, int start, int endExclusive) {
        try {
            JsonNode node = JSON.readTree(text.substring(start, endExclusive));
            return node != null && node.isTextual() ? node.textValue() : null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static String redactPlainText(String text) {
        String safe = BEARER_CREDENTIAL.matcher(text).replaceAll("$1 " + REDACTED);
        safe = HEADER_BLOCK.matcher(safe).replaceAll("$1" + REDACTED);
        safe = COOKIE_HEADER.matcher(safe).replaceAll("$1" + REDACTED);
        safe = NAMED_CREDENTIAL.matcher(safe).replaceAll("$1" + REDACTED);
        return KNOWN_TOKEN.matcher(safe).replaceAll(REDACTED);
    }

    private static JsonNode redactNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode safe = JSON.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                safe.set(field.getKey(),
                        isCredentialField(field.getKey())
                                ? JSON.getNodeFactory().textNode(REDACTED)
                                : redactNode(field.getValue()));
            }
            return safe;
        }
        if (node.isArray()) {
            ArrayNode safe = JSON.createArrayNode();
            node.forEach(value -> safe.add(redactNode(value)));
            return safe;
        }
        if (node.isTextual()) {
            return JSON.getNodeFactory().textNode(redactText(node.textValue()));
        }
        return node.deepCopy();
    }

    private static boolean isCredentialField(String fieldName) {
        String normalized = fieldName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return normalized.contains("password") || normalized.contains("passwd") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("apikey") || normalized.contains("authorization")
                || normalized.contains("credential") || normalized.contains("privatekey")
                || normalized.contains("accesskey") || normalized.contains("header") || normalized.contains("cookie");
    }
}
