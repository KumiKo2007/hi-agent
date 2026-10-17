package org.xhy.application.run.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;

/** Appends traceable source references to the body that will be shown in a write approval. */
public final class RunEvidenceCitationAppender {
    private static final ObjectMapper JSON = new ObjectMapper();

    private RunEvidenceCitationAppender() {
    }

    /**
     * Appends citations from the run's pinned evidence to a textual MCP {@code body} parameter.
     * The modified arguments must be persisted and shown before the owner approves them.
     */
    public static String appendToBody(String arguments, List<KnowledgeCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            throw new BusinessException("资料依据不足，已停止外部写入");
        }
        try {
            JsonNode parsed = JSON.readTree(arguments);
            if (!(parsed instanceof ObjectNode bodyArguments)) {
                throw new BusinessException("写入动作未提供可引用的正文，已停止外部写入");
            }
            JsonNode bodyValue = bodyArguments.get("body");
            if (bodyValue == null || !bodyValue.isTextual()) {
                throw new BusinessException("写入动作未提供可引用的正文，已停止外部写入");
            }

            StringBuilder appendix = new StringBuilder("### Sources");
            for (int index = 0; index < citations.size(); index++) {
                KnowledgeCitation citation = citations.get(index);
                String fileName = safeLabel(citation.getFileName());
                String page = citation.getPage() == null ? "page not specified" : "page " + citation.getPage();
                appendix.append("\n- [S").append(index + 1).append("] `").append(fileName).append("`, ")
                        .append(page);
            }
            String currentBody = bodyValue.asText().stripTrailing();
            bodyArguments.put("body", currentBody + "\n\n---\n\n" + appendix);
            return JSON.writeValueAsString(bodyArguments);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("写入动作参数不是有效 JSON");
        }
    }

    private static String safeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "unnamed source";
        }
        return value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replace('`', '\u2019').trim();
    }
}
