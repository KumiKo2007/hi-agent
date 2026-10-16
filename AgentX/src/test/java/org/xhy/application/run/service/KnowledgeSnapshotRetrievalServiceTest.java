package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.rag.model.RagVersionDocumentEntity;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.model.RagVersionFileEntity;
import org.xhy.domain.rag.repository.RagVersionDocumentRepository;
import org.xhy.domain.rag.repository.RagVersionFileRepository;
import org.xhy.domain.rag.repository.RagVersionRepository;
import org.xhy.domain.run.model.RunEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Frozen knowledge retrieval is an observable run boundary, rather than a live RAG query. */
class KnowledgeSnapshotRetrievalServiceTest {

    @Test
    void retrievesOnlyThePinnedVersionAndReturnsOpenableCitation() {
        RagVersionRepository versions = mock(RagVersionRepository.class);
        RagVersionDocumentRepository documents = mock(RagVersionDocumentRepository.class);
        RagVersionFileRepository files = mock(RagVersionFileRepository.class);

        RagVersionEntity version = readyVersion("version-1", "dataset-1", "owner");
        RagVersionFileEntity file = versionFile("version-file-1", "design.md");
        RagVersionDocumentEntity document = versionDocument("version-1", "version-file-1", "doc-1",
                "The gateway retries failed requests.", 7);
        when(versions.selectOne(any())).thenReturn(version);
        when(documents.selectList(any())).thenReturn(List.of(document));
        when(files.selectList(any())).thenReturn(List.of(file));

        KnowledgeSnapshotRetrievalService service = new KnowledgeSnapshotRetrievalService(versions, documents, files);

        KnowledgeRetrievalResult result = service.retrieve("version-1", "gateway retries", "owner");

        assertTrue(result.hasEvidence());
        assertTrue(result.canPublish());
        assertEquals("version-1", result.getKnowledgeVersionId());
        assertEquals(1, result.getCitations().size());
        KnowledgeCitation citation = result.getCitations().get(0);
        assertEquals("doc-1", citation.getDocumentId());
        assertEquals("version-file-1", citation.getFileId());
        assertEquals("design.md", citation.getFileName());
        assertEquals(7, citation.getPage());
        assertEquals("The gateway retries failed requests.", citation.getContent());
        assertTrue(citation.getSourceUrl().contains("version-1"));
        assertTrue(citation.getSourceUrl().contains("doc-1"));
    }

    @Test
    void returnsExplicitInsufficientEvidenceWithoutCitations() {
        RagVersionRepository versions = mock(RagVersionRepository.class);
        RagVersionDocumentRepository documents = mock(RagVersionDocumentRepository.class);
        RagVersionFileRepository files = mock(RagVersionFileRepository.class);

        when(versions.selectOne(any())).thenReturn(readyVersion("version-1", "dataset-1", "owner"));
        when(documents.selectList(any())).thenReturn(
                List.of(versionDocument("version-1", "file-1", "doc-1", "Only unrelated project information.", 1)));
        when(files.selectList(any())).thenReturn(List.of(versionFile("file-1", "README.md")));

        KnowledgeSnapshotRetrievalService service = new KnowledgeSnapshotRetrievalService(versions, documents, files);

        KnowledgeRetrievalResult result = service.retrieve("version-1", "database migration", "owner");

        assertFalse(result.hasEvidence());
        assertFalse(result.canPublish());
        assertTrue(result.getCitations().isEmpty());
        assertTrue(result.getMessage().contains("不足"));
    }

    @Test
    void runKeepsTheVersionItPinnedWhenAnewerVersionExists() {
        RagVersionEntity pinned = readyVersion("version-1", "dataset-1", "owner");
        RagVersionEntity newer = readyVersion("version-2", "dataset-1", "owner");

        RunKnowledgeVersionResolver resolver = (agentId, userId) -> pinned.getId();
        assertEquals("version-1", resolver.resolve("agent-1", "owner"));
        assertEquals("version-2", newer.getId());
        // The run stores the resolver's value; changing the dataset's latest pointer cannot change it.
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "goal", resolver.resolve("agent-1", "owner"));
        assertEquals("version-1", run.getKnowledgeVersionId());
    }

    private static RagVersionEntity readyVersion(String id, String datasetId, String userId) {
        RagVersionEntity version = new RagVersionEntity();
        version.setId(id);
        version.setOriginalRagId(datasetId);
        version.setUserId(userId);
        version.setSearchReady(true);
        return version;
    }

    private static RagVersionFileEntity versionFile(String id, String name) {
        RagVersionFileEntity file = new RagVersionFileEntity();
        file.setId(id);
        file.setFileName(name);
        return file;
    }

    private static RagVersionDocumentEntity versionDocument(String versionId, String fileId, String id, String content,
            int page) {
        RagVersionDocumentEntity document = new RagVersionDocumentEntity();
        document.setId(id);
        document.setRagVersionId(versionId);
        document.setRagVersionFileId(fileId);
        document.setOriginalDocumentId("original-" + id);
        document.setContent(content);
        document.setPage(page);
        return document;
    }

}
