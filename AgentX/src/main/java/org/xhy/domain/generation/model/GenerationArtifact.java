package org.xhy.domain.generation.model;
import java.util.Map;
/** 产物目标与当前修订指针；旧修订不因编辑或确认而改变。 */
public record GenerationArtifact(String id, String userId, String kind, String selectionId, String knowledgeVersionId,
        String inputRevisionId, Map<String, Object> parameters, String status, String currentRunId,
        String currentRevisionId, String confirmedRevisionId, String createdAt, GenerationRun run,
        ArtifactRevision revision) {
    public GenerationArtifact withState(String status, String runId, String revisionId, String confirmed,
            GenerationRun run, ArtifactRevision revision) {
        return new GenerationArtifact(id, userId, kind, selectionId, knowledgeVersionId, inputRevisionId, parameters,
                status, runId, revisionId, confirmed, createdAt, run, revision);
    }
}
