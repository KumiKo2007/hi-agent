package org.xhy.infrastructure.repository.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.xhy.domain.generation.model.*;
import org.xhy.domain.generation.repository.GenerationStore;

/** PostgreSQL 保存权威状态，CAS 控制并发生成、编辑与确认。 */
@Repository
public class JdbcGenerationStore implements GenerationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public JdbcGenerationStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(
                new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    @Override
    public void create(GenerationArtifact artifact, GenerationRun run) {
        tx.executeWithoutResult(s -> {
            jdbc.update(
                    "INSERT INTO generation_artifacts(id,user_id,kind,status,current_run_id,artifact_json) VALUES(?,?,?,?,?,CAST(? AS jsonb))",
                    artifact.id(), artifact.userId(), artifact.kind(), artifact.status(), run.id(), encode(artifact));
            jdbc.update(
                    "INSERT INTO generation_runs(id,artifact_id,user_id,status,run_json) VALUES(?,?,?,?,CAST(? AS jsonb))",
                    run.id(), artifact.id(), run.userId(), run.status(), encode(run));
        });
    }
    @Override
    public GenerationArtifact findOwned(String id, String userId) {
        var rows = jdbc.query(
                "SELECT artifact_json::text,status,current_run_id,current_revision_id,confirmed_revision_id FROM generation_artifacts WHERE id=? AND user_id=?",
                (r, n) -> decode(r.getString(1), GenerationArtifact.class).withState(r.getString(2), r.getString(3),
                        r.getString(4), r.getString(5), null, null),
                id, userId);
        if (rows.isEmpty())
            return null;
        var a = rows.get(0);
        var runs = jdbc.query(
                "SELECT run_json::text,status,error,output_revision_id,updated_at::text FROM generation_runs WHERE id=? AND user_id=?",
                (r, n) -> decode(r.getString(1), GenerationRun.class).withState(r.getString(2), r.getString(3),
                        r.getString(4), r.getString(5)),
                a.currentRunId(), userId);
        return a.withState(a.status(), a.currentRunId(), a.currentRevisionId(), a.confirmedRevisionId(),
                runs.isEmpty() ? null : runs.get(0),
                a.currentRevisionId() == null ? null : revisionOwned(id, a.currentRevisionId(), userId));
    }
    @Override
    public List<GenerationArtifact> listOwned(String userId, String kind) {
        return jdbc.query(
                "SELECT id FROM generation_artifacts WHERE user_id=? AND kind=? ORDER BY created_at DESC LIMIT 100",
                (r, n) -> r.getString(1), userId, kind).stream().map(id -> findOwned(id, userId)).toList();
    }
    @Override
    public ArtifactRevision revisionOwned(String artifactId, String revisionId, String userId) {
        var rows = jdbc.query(
                "SELECT revision_json::text FROM artifact_revisions WHERE id=? AND artifact_id=? AND user_id=?",
                (r, n) -> decode(r.getString(1), ArtifactRevision.class), revisionId, artifactId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    @Override
    public boolean claim(String runId, String userId) {
        return jdbc.update(
                "UPDATE generation_runs SET status='RUNNING',updated_at=now() WHERE id=? AND user_id=? AND status='PENDING'",
                runId, userId) == 1;
    }
    @Override
    public void fail(String runId, String userId, String error) {
        jdbc.update(
                "UPDATE generation_runs SET status='FAILED',error=?,updated_at=now() WHERE id=? AND user_id=? AND status='RUNNING'",
                error, runId, userId);
    }
    @Override
    public boolean complete(String artifactId, String runId, String userId, ArtifactRevision revision) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            int count = jdbc.update(
                    "UPDATE generation_runs SET status='SUCCEEDED',output_revision_id=?,updated_at=now() WHERE id=? AND artifact_id=? AND user_id=? AND status='RUNNING'",
                    revision.id(), runId, artifactId, userId);
            if (count != 1)
                return false;
            int changed = jdbc.update(
                    "UPDATE generation_artifacts SET status='DRAFT',current_revision_id=?,confirmed_revision_id=null,updated_at=now() WHERE id=? AND user_id=? AND current_run_id=? AND current_revision_id IS NULL",
                    revision.id(), artifactId, userId, runId);
            if (changed != 1) {
                s.setRollbackOnly();
                return false;
            }
            insert(revision);
            return true;
        }));
    }
    @Override
    public boolean appendRevision(String artifactId, String expected, String userId, ArtifactRevision revision) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            int count = jdbc.update(
                    "UPDATE generation_artifacts SET status='DRAFT',current_revision_id=?,confirmed_revision_id=null,updated_at=now() WHERE id=? AND user_id=? AND current_revision_id=?",
                    revision.id(), artifactId, userId, expected);
            if (count != 1)
                return false;
            insert(revision);
            return true;
        }));
    }
    @Override
    public boolean confirm(String artifactId, String expected, String userId) {
        return jdbc.update(
                "UPDATE generation_artifacts SET status='CONFIRMED',confirmed_revision_id=?,updated_at=now() WHERE id=? AND user_id=? AND current_revision_id=?",
                expected, artifactId, userId, expected) == 1;
    }
    private void insert(ArtifactRevision r) {
        jdbc.update(
                "INSERT INTO artifact_revisions(id,artifact_id,user_id,run_id,parent_revision_id,input_revision_id,knowledge_version_id,revision_json) VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb))",
                r.id(), r.artifactId(), r.userId(), r.runId(), r.parentRevisionId(), r.inputRevisionId(),
                r.knowledgeVersionId(), encode(r));
    }
    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("内容保存失败", e);
        }
    }
    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("内容读取失败", e);
        }
    }
}
