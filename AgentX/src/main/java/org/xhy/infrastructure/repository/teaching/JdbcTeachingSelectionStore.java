package org.xhy.infrastructure.repository.teaching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.xhy.domain.teaching.model.TeachingSelection;
import org.xhy.domain.teaching.repository.TeachingSelectionStore;

/** 持久化固定来源内容，读取时绝不回退到当前知识库内容。 */
@Repository
public class JdbcTeachingSelectionStore implements TeachingSelectionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcTeachingSelectionStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public TeachingSelection.Version findOwnedVersion(String datasetId, String versionId, String userId) {
        var versions = jdbc.query("""
                SELECT id, version FROM rag_versions
                WHERE id=? AND original_rag_id=? AND user_id=? AND search_ready=true AND deleted_at IS NULL
                """, (row, n) -> new String[]{row.getString("id"), row.getString("version")}, versionId, datasetId,
                userId);
        if (versions.isEmpty()) {
            return null;
        }
        var files = jdbc.query("""
                SELECT id,file_name,file_type,file_page_size FROM rag_version_files
                WHERE rag_version_id=? AND deleted_at IS NULL ORDER BY id
                """, (row, n) -> new TeachingSelection.File(row.getString("id"), row.getString("file_name"),
                row.getString("file_type"), (Integer) row.getObject("file_page_size")), versionId);
        var sources = jdbc.query(
                """
                        SELECT d.id,d.rag_version_file_id,f.file_name,f.file_type,d.page,d.content,d.source_locator_type,d.source_locator,d.coverage_warnings
                        FROM rag_version_documents d JOIN rag_version_files f ON f.id=d.rag_version_file_id
                        AND f.rag_version_id=d.rag_version_id
                        WHERE d.rag_version_id=? AND d.page IS NOT NULL AND d.page>=0 AND d.deleted_at IS NULL AND f.deleted_at IS NULL ORDER BY f.id,d.page,d.id
                        """,
                (row, n) -> {
                    boolean pdf = "pdf".equalsIgnoreCase(row.getString("file_type"))
                            || "application/pdf".equalsIgnoreCase(row.getString("file_type"));
                    String type = row.getString("source_locator_type");
                    Integer paragraph = null;
                    String heading = null;
                    var warnings = new java.util.ArrayList<String>();
                    try {
                        if (row.getString("coverage_warnings") != null) {
                            for (var warning : json.readTree(row.getString("coverage_warnings")))
                                warnings.add(warning.asText());
                        }
                        if ("WORD_PARAGRAPH".equals(type)) {
                            var locator = json.readTree(row.getString("source_locator"));
                            paragraph = locator.path("paragraphIndex").asInt();
                            heading = locator.path("heading").asText("");
                            if (paragraph < 1 || paragraph != row.getInt("page") + 1)
                                throw new IllegalArgumentException();
                        }
                    } catch (Exception invalid) {
                        paragraph = null;
                        warnings.add("来源定位或覆盖信息损坏，请重新处理原文件并固定新版本");
                    }
                    if (!pdf && paragraph == null)
                        warnings.add("历史来源缺少可靠原文段落定位，请重新处理 Word 并固定新版本");
                    return new TeachingSelection.Source(row.getString("id"), row.getString("rag_version_file_id"),
                            row.getString("file_name"), pdf || paragraph == null ? row.getInt("page") + 1 : null,
                            paragraph != null ? "WORD_PARAGRAPH" : pdf ? "FILE_PAGE" : "DOCUMENT_UNIT",
                            row.getString("content"), null, paragraph, heading, List.copyOf(warnings));
                }, versionId);
        return new TeachingSelection.Version(versionId, versions.get(0)[1], files, sources);
    }

    @Override
    public void save(TeachingSelection selection, String userId) {
        try {
            jdbc.update("INSERT INTO teaching_selections(id,user_id,selection_json) VALUES(?,?,CAST(? AS jsonb))",
                    selection.id(), userId, json.writeValueAsString(selection));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("选材保存失败", e);
        }
    }

    @Override
    public TeachingSelection findOwned(String id, String userId) {
        var rows = jdbc.query("SELECT selection_json::text FROM teaching_selections WHERE id=? AND user_id=?",
                (row, n) -> decode(row.getString(1)), id, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<TeachingSelection> listOwned(String userId) {
        return jdbc.query(
                "SELECT selection_json::text FROM teaching_selections WHERE user_id=? ORDER BY created_at DESC",
                (row, n) -> decode(row.getString(1)), userId);
    }

    private TeachingSelection decode(String value) {
        try {
            return json.readValue(value, TeachingSelection.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("选材读取失败", e);
        }
    }
}
