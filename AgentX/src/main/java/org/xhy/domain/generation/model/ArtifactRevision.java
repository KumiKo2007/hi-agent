package org.xhy.domain.generation.model;
import java.util.Map;
/** 不可变产物修订；同一份双稿可共享该修订身份。 */
public record ArtifactRevision(String id, String artifactId, String userId, String runId, String parentRevisionId,
        String inputRevisionId, String knowledgeVersionId, Map<String, Object> content, String createdAt) {
}
