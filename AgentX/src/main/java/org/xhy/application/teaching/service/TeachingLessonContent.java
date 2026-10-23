package org.xhy.application.teaching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.xhy.domain.teaching.model.TeachingSelection;
import org.xhy.infrastructure.exception.BusinessException;

/** 模型响应只转为允许的内容字段；来源定位始终由固定选材解析。 */
final class TeachingLessonContent {
    private final ObjectMapper json;
    TeachingLessonContent(ObjectMapper json) {
        this.json = json;
    }

    Map<String, Object> validate(String answer, Map<String, Object> parameters, TeachingSelection selection)
            throws Exception {
        if (answer == null || answer.length() > 300000)
            throw invalid();
        JsonNode root = json.readTree(answer);
        if (!root.isObject())
            throw invalid();
        var teacher = root.path("teacher");
        var stages = new ArrayList<Map<String, Object>>();
        int total = 0;
        for (var stage : array(teacher, "stages", 30)) {
            var minutes = stage.path("minutes");
            if (!minutes.isIntegralNumber() || !minutes.canConvertToInt() || minutes.asInt() < 1
                    || minutes.asInt() > 180)
                throw invalid();
            total += minutes.asInt();
            stages.add(Map.of("title", text(stage, "title", 300), "minutes", minutes.asInt(), "guidance",
                    text(stage, "guidance", 16000)));
        }
        if (total != ((Number) parameters.get("lessonMinutes")).intValue())
            throw invalid();
        var sources = new LinkedHashMap<String, TeachingSelection.Source>();
        selection.sources().forEach(source -> sources.put(source.id(), source));
        var ids = new HashSet<String>();
        var used = new LinkedHashSet<String>();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("schemaVersion", 1);
        content.put("title", parameters.get("lessonTitle"));
        content.put("student", Map.of("objectives", strings(root.path("student"), "objectives")));
        content.put("teacher",
                Map.of("objectives", strings(teacher, "objectives"), "difficulties", strings(teacher, "difficulties"),
                        "stages", stages, "prompts", strings(teacher, "prompts"), "misconceptions",
                        strings(teacher, "misconceptions")));
        for (String key : List.of("knowledge", "derivations", "examples")) {
            var blocks = new ArrayList<Map<String, Object>>();
            for (var block : array(root, key, 40)) {
                String id = text(block, "id", 100);
                if (!ids.add(id))
                    throw invalid();
                var evidence = block.path("evidence");
                String kind = text(evidence, "kind", 30);
                if (!List.of("SOURCE", "SUPPLEMENT", "UNSUPPORTED").contains(kind))
                    throw invalid();
                var sourceIds = evidence.path("sourceIds");
                if (!sourceIds.isArray() || sourceIds.size() > 30)
                    throw invalid();
                var refs = new LinkedHashSet<String>();
                for (var sourceId : sourceIds) {
                    if (!sourceId.isTextual() || !sources.containsKey(sourceId.asText()))
                        throw invalid();
                    refs.add(sourceId.asText());
                }
                if ("SOURCE".equals(kind) && refs.isEmpty())
                    throw invalid();
                String note = optionalText(evidence, "note", 2000);
                if (!"SOURCE".equals(kind) && note.isBlank())
                    throw invalid();
                used.addAll(refs);
                var clean = new LinkedHashMap<String, Object>();
                clean.put("id", id);
                clean.put("title", text(block, "title", 300));
                clean.put("text", text(block, "text", 16000));
                clean.put("evidence", Map.of("kind", kind, "sourceIds", refs, "note", note));
                if ("examples".equals(key))
                    clean.put("solution", text(block, "solution", 16000));
                blocks.add(clean);
            }
            content.put(key, blocks);
        }
        content.put("sources", used.stream().map(sources::get).toList());
        content.put("reviewStatus", "TEACHER_REVIEW_REQUIRED");
        return content;
    }
    private JsonNode array(JsonNode parent, String key, int max) {
        var value = parent.path(key);
        if (!value.isArray() || value.isEmpty() || value.size() > max)
            throw invalid();
        return value;
    }
    private List<String> strings(JsonNode parent, String key) {
        var list = new ArrayList<String>();
        for (var item : array(parent, key, 30)) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 2000)
                throw invalid();
            list.add(item.asText());
        }
        return list;
    }
    private String text(JsonNode parent, String key, int max) {
        String value = optionalText(parent, key, max);
        if (value.isBlank())
            throw invalid();
        return value;
    }
    private String optionalText(JsonNode parent, String key, int max) {
        var value = parent.path(key);
        if (value.isMissingNode() || value.isNull())
            return "";
        if (!value.isTextual() || value.asText().length() > max)
            throw invalid();
        return value.asText();
    }
    private BusinessException invalid() {
        return new BusinessException("模型双稿内容或来源格式无效");
    }
}
