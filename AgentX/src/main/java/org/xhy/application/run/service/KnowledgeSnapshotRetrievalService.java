package org.xhy.application.run.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.xhy.domain.rag.model.RagVersionDocumentEntity;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.model.RagVersionFileEntity;
import org.xhy.domain.rag.repository.RagVersionDocumentRepository;
import org.xhy.domain.rag.repository.RagVersionFileRepository;
import org.xhy.domain.rag.repository.RagVersionRepository;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Searches only immutable {@code rag_version_documents} rows. The original dataset and live RAG search are
 * intentionally outside this boundary so an in-flight run cannot observe a later upload. */
@Service
public class KnowledgeSnapshotRetrievalService {

    private static final int MAX_CITATIONS = 5;

    private final RagVersionRepository versions;
    private final RagVersionDocumentRepository documents;
    private final RagVersionFileRepository files;

    public KnowledgeSnapshotRetrievalService(RagVersionRepository versions, RagVersionDocumentRepository documents,
            RagVersionFileRepository files) {
        this.versions = versions;
        this.documents = documents;
        this.files = files;
    }

    /** Query one run-pinned version. The owner and ready flag are checked on every read, including citation reads. */
    public KnowledgeRetrievalResult retrieve(String knowledgeVersionId, String query, String userId) {
        RagVersionEntity version = findReadyVersion(knowledgeVersionId, userId);
        if (query == null || query.isBlank()) {
            return insufficient(knowledgeVersionId, "没有检索问题，资料依据不足，不能自动发布");
        }

        List<RagVersionDocumentEntity> snapshotDocuments = documents
                .selectList(Wrappers.<RagVersionDocumentEntity>lambdaQuery()
                        .eq(RagVersionDocumentEntity::getRagVersionId, knowledgeVersionId));
        if (snapshotDocuments.isEmpty()) {
            return insufficient(knowledgeVersionId, "固定知识版本没有可检索资料，不能自动发布");
        }

        Map<String, RagVersionFileEntity> snapshotFiles = loadFiles(snapshotDocuments);
        List<RankedDocument> ranked = new ArrayList<>();
        for (RagVersionDocumentEntity document : snapshotDocuments) {
            int score = score(document.getContent(), query);
            if (score > 0) {
                ranked.add(new RankedDocument(document, score));
            }
        }
        ranked.sort(Comparator.comparingInt(RankedDocument::score).reversed()
                .thenComparing(item -> item.document().getId(), Comparator.nullsLast(String::compareTo)));

        List<KnowledgeCitation> citations = ranked.stream().limit(MAX_CITATIONS).map(
                item -> toCitation(version, item.document(), snapshotFiles.get(item.document().getRagVersionFileId())))
                .toList();
        if (citations.isEmpty()) {
            return insufficient(knowledgeVersionId, "固定知识版本中没有支持该目标的资料，资料依据不足，不能自动发布");
        }
        return new KnowledgeRetrievalResult(knowledgeVersionId, true, "已从固定知识版本检索到依据", citations);
    }

    /** Read a single cited row, which gives Web clients a stable source link. */
    public KnowledgeCitation findCitation(String knowledgeVersionId, String documentId, String userId) {
        RagVersionEntity version = findReadyVersion(knowledgeVersionId, userId);
        RagVersionDocumentEntity document = documents.selectOne(
                Wrappers.<RagVersionDocumentEntity>lambdaQuery().eq(RagVersionDocumentEntity::getId, documentId)
                        .eq(RagVersionDocumentEntity::getRagVersionId, knowledgeVersionId));
        if (document == null) {
            throw new BusinessException("引用资料不存在或无访问权限");
        }
        RagVersionFileEntity file = files.selectById(document.getRagVersionFileId());
        return toCitation(version, document, file);
    }

    private RagVersionEntity findReadyVersion(String knowledgeVersionId, String userId) {
        if (knowledgeVersionId == null || knowledgeVersionId.isBlank()) {
            throw new BusinessException("运行尚未固定可检索知识版本");
        }
        RagVersionEntity version = versions
                .selectOne(Wrappers.<RagVersionEntity>lambdaQuery().eq(RagVersionEntity::getId, knowledgeVersionId)
                        .eq(RagVersionEntity::getUserId, userId).eq(RagVersionEntity::getSearchReady, true));
        if (version == null) {
            throw new BusinessException("可检索知识版本不存在或无访问权限");
        }
        return version;
    }

    private Map<String, RagVersionFileEntity> loadFiles(List<RagVersionDocumentEntity> documents) {
        List<String> fileIds = documents.stream().map(RagVersionDocumentEntity::getRagVersionFileId)
                .filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        List<RagVersionFileEntity> snapshotFiles = files
                .selectList(Wrappers.<RagVersionFileEntity>lambdaQuery().in(RagVersionFileEntity::getId, fileIds));
        Map<String, RagVersionFileEntity> byId = new HashMap<>();
        snapshotFiles.forEach(file -> byId.put(file.getId(), file));
        return byId;
    }

    private KnowledgeCitation toCitation(RagVersionEntity version, RagVersionDocumentEntity document,
            RagVersionFileEntity file) {
        KnowledgeCitation citation = new KnowledgeCitation();
        citation.setKnowledgeVersionId(version.getId());
        citation.setDocumentId(document.getId());
        citation.setOriginalDocumentId(document.getOriginalDocumentId());
        citation.setFileId(document.getRagVersionFileId());
        citation.setFileName(file == null ? null : file.getFileName());
        citation.setPage(document.getPage());
        citation.setContent(document.getContent());
        citation.setSourceUrl("/rag/datasets/" + version.getOriginalRagId() + "/ready-versions/" + version.getId()
                + "/documents/" + document.getId());
        return citation;
    }

    private int score(String content, String query) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedContent.contains(normalizedQuery)) {
            return 100 + normalizedQuery.length();
        }
        String[] terms = normalizedQuery.split("[^\\p{L}\\p{N}_-]+");
        int matched = 0;
        for (String term : terms) {
            if (!term.isBlank() && normalizedContent.contains(term)) {
                matched++;
            }
        }
        return matched;
    }

    private KnowledgeRetrievalResult insufficient(String knowledgeVersionId, String message) {
        return new KnowledgeRetrievalResult(knowledgeVersionId, false, message, List.of());
    }

    private record RankedDocument(RagVersionDocumentEntity document, int score) {
    }
}
