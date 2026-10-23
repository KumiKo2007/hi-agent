package org.xhy.infrastructure.repository.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.xhy.domain.teaching.model.TeachingOcr;
import org.xhy.domain.teaching.repository.TeachingOcrStore;

/** 所有读取先验证版本或原文件归属，返回完整物理页范围而非只返回成功页。 */
@Repository
public class JdbcTeachingOcrStore implements TeachingOcrStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcTeachingOcrStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }
    @Override
    public TeachingOcr.StoredFile findOwnedVersionFile(String versionId, String fileId, String userId) {
        var files = jdbc.query("""
                SELECT f.file_name,f.file_page_size,f.file_path FROM rag_version_files f JOIN rag_versions v
                ON v.id=f.rag_version_id WHERE v.id=? AND f.id=? AND v.user_id=?
                AND v.search_ready=true AND v.deleted_at IS NULL AND f.deleted_at IS NULL
                AND lower(f.file_type) IN ('pdf','application/pdf')
                """, (r, n) -> new Object[]{r.getString(1), r.getObject(2), r.getString(3)}, versionId, fileId, userId);
        if (files.isEmpty())
            return null;
        var f = files.get(0);
        var pages = jdbc.query("""
                SELECT id,page,content,coverage_warnings FROM rag_version_documents
                WHERE rag_version_id=? AND rag_version_file_id=? AND deleted_at IS NULL ORDER BY page,id
                """, (r, n) -> page(r.getString(1), r.getInt(2) + 1, r.getString(3), r.getString(4)), versionId,
                fileId);
        var reviewed = pages.stream().map(page -> {
            String hash = TeachingOcr.fingerprint(page.sourceId(), page.content(), page.coverageWarnings());
            var rows = jdbc.query(
                    "SELECT note,reviewed_at FROM teaching_source_reviews WHERE user_id=? AND version_id=? AND source_id=? AND content_hash=?",
                    (r, n) -> new TeachingOcr.Review(r.getString(1), r.getString(2), hash), userId, versionId,
                    page.sourceId(), hash);
            return new TeachingOcr.Page(page.sourceId(), page.physicalPage(), page.content(), page.coverageWarnings(),
                    page.status(), rows.isEmpty() ? null : rows.get(0));
        }).toList();
        return new TeachingOcr.StoredFile(versionId, fileId, (String) f[0],
                f[1] == null ? null : ((Number) f[1]).intValue(), (String) f[2], null, null, reviewed);
    }
    @Override
    public TeachingOcr.StoredFile findOwnedCurrentFile(String fileId, String userId) {
        var files = jdbc.query(
                "SELECT original_filename,file_page_size,url,processing_status,processing_error FROM file_detail WHERE id=? AND user_id=? AND deleted_at IS NULL AND lower(ext)='pdf'",
                (r, n) -> new Object[]{r.getString(1), r.getObject(2), r.getString(3), r.getObject(4), r.getString(5)},
                fileId, userId);
        if (files.isEmpty())
            return null;
        var f = files.get(0);
        var pages = jdbc.query(
                "SELECT id,page,content,coverage_warnings FROM document_unit WHERE file_id=? AND deleted_at IS NULL ORDER BY page,id",
                (r, n) -> page(r.getString(1), r.getInt(2) + 1, r.getString(3), r.getString(4)), fileId);
        return new TeachingOcr.StoredFile(null, fileId, (String) f[0], f[1] == null ? null : ((Number) f[1]).intValue(),
                (String) f[2], f[3] == null ? null : ((Number) f[3]).intValue(), (String) f[4], pages);
    }
    private TeachingOcr.Page page(String id, int page, String content, String value) {
        List<String> warnings;
        try {
            warnings = value == null || value.isBlank()
                    ? List.of()
                    : json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            warnings = List.of("OCR_METADATA_GAP: 来源覆盖信息损坏，请重新处理此页");
        }
        return new TeachingOcr.Page(id, page, content, warnings, null, null);
    }
    @Override
    public void saveReview(String versionId, String sourceId, String userId, TeachingOcr.Review review) {
        jdbc.update("""
                INSERT INTO teaching_source_reviews(id,user_id,version_id,source_id,content_hash,note,reviewed_at)
                VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,version_id,source_id,content_hash) DO NOTHING
                """, UUID.randomUUID().toString(), userId, versionId, sourceId, review.contentHash(), review.note(),
                review.reviewedAt());
    }
}
