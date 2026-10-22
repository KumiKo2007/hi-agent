package org.xhy.domain.generation.model;
import java.util.Map;
/** 固定输入、模型和来源的一次独立内容生成运行。 */
public record GenerationRun(String id, String artifactId, String userId, String goal, String kind, String modelId,
        String connectionType, String selectionId, String knowledgeVersionId, String inputRevisionId,
        Map<String, Object> parameters, String status, String error, String outputRevisionId, String createdAt,
        String updatedAt) {
    public GenerationRun withState(String status, String error, String revision, String updated) {
        return new GenerationRun(id, artifactId, userId, goal, kind, modelId, connectionType, selectionId,
                knowledgeVersionId, inputRevisionId, parameters, status, error, revision, createdAt, updated);
    }
}
