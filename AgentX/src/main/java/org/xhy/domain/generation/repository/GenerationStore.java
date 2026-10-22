package org.xhy.domain.generation.repository;
import java.util.List;
import org.xhy.domain.generation.model.*;
/** 内容运行与修订的持久边界；所有面向用户的读取均受所有者约束。 */
public interface GenerationStore {
    void create(GenerationArtifact artifact, GenerationRun run);
    GenerationArtifact findOwned(String id, String userId);
    List<GenerationArtifact> listOwned(String userId, String kind);
    ArtifactRevision revisionOwned(String artifactId, String revisionId, String userId);
    boolean claim(String runId, String userId);
    void fail(String runId, String userId, String error);
    boolean complete(String artifactId, String runId, String userId, ArtifactRevision revision);
    boolean appendRevision(String artifactId, String expectedRevisionId, String userId, ArtifactRevision revision);
    boolean confirm(String artifactId, String expectedRevisionId, String userId);
}
