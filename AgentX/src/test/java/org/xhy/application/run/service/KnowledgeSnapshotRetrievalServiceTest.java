package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.rag.model.RagVersionDocumentEntity;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.model.RagVersionFileEntity;
import org.xhy.domain.rag.repository.RagVersionDocumentRepository;
import org.xhy.domain.rag.repository.RagVersionFileRepository;
import org.xhy.domain.rag.repository.RagVersionRepository;
import org.xhy.domain.run.model.RunEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frozen knowledge retrieval is an observable run boundary, rather than a live RAG query. */
class KnowledgeSnapshotRetrievalServiceTest {

    @Test
    void retrievesOnlyThePinnedVersionAndReturnsOpenableCitation() {
        RagVersionEntity version = readyVersion("version-1", "dataset-1", "owner");
        RagVersionFileEntity file = versionFile("version-file-1", "design.md");
        RagVersionDocumentEntity document = versionDocument("version-1", "version-file-1", "doc-1",
                "The gateway retries failed requests.", 7);
        KnowledgeSnapshotRetrievalService service = retrievalService(version, List.of(document), List.of(file));

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
        KnowledgeSnapshotRetrievalService service = retrievalService(readyVersion("version-1", "dataset-1", "owner"),
                List.of(versionDocument("version-1", "file-1", "doc-1", "Only unrelated project information.", 1)),
                List.of(versionFile("file-1", "README.md")));

        KnowledgeRetrievalResult result = service.retrieve("version-1", "database migration", "owner");

        assertFalse(result.hasEvidence());
        assertFalse(result.canPublish());
        assertTrue(result.getCitations().isEmpty());
        assertTrue(result.getMessage().contains("不足"));
    }

    @Test
    void commonIssueRequestWordsAloneDoNotCountAsEvidence() {
        KnowledgeSnapshotRetrievalService service = retrievalService(readyVersion("version-1", "dataset-1", "owner"),
                List.of(versionDocument("version-1", "file-1", "doc-1",
                        "Please create an issue for the project and add it to the repository.", 1)),
                List.of(versionFile("file-1", "README.md")));

        KnowledgeRetrievalResult result = service.retrieve("version-1", "Create an issue for the project", "owner");

        assertFalse(result.hasEvidence());
        assertFalse(result.canPublish());
        assertTrue(result.getCitations().isEmpty());
    }

    @Test
    void oneTopicWordMatchIsNotEnoughToCiteOrPublish() {
        KnowledgeSnapshotRetrievalService service = retrievalService(readyVersion("version-1", "dataset-1", "owner"),
                List.of(versionDocument("version-1", "file-1", "doc-1",
                        "The gateway is a shared entry point for services.", 1)),
                List.of(versionFile("file-1", "architecture.md")));

        KnowledgeRetrievalResult result = service.retrieve("version-1", "gateway retries", "owner");

        assertFalse(result.hasEvidence());
        assertFalse(result.canPublish());
        assertTrue(result.getCitations().isEmpty());
    }

    @Test
    void aFirstTopicMatchCannotAuthorizeAUnsupportedMultiTopicGoal() {
        KnowledgeSnapshotRetrievalService service = retrievalService(readyVersion("version-1", "dataset-1", "owner"),
                List.of(versionDocument("version-1", "file-1", "doc-1",
                        "The database migration follows the existing backup procedure.", 1)),
                List.of(versionFile("file-1", "migration.md")));

        KnowledgeRetrievalResult result = service.retrieve("version-1",
                "database migration, token rotation, recovery", "owner");

        assertFalse(result.hasEvidence());
        assertFalse(result.canPublish());
        assertTrue(result.getCitations().isEmpty());
    }

    @Test
    void citesMultiTopicGoalWhenThePinnedSourceSupportsEveryTopic() {
        KnowledgeSnapshotRetrievalService service = retrievalService(readyVersion("version-1", "dataset-1", "owner"),
                List.of(versionDocument("version-1", "file-1", "doc-1",
                        "The database migration coordinates token rotation and recovery.", 1)),
                List.of(versionFile("file-1", "migration.md")));

        KnowledgeRetrievalResult result = service.retrieve("version-1",
                "database migration, token rotation, recovery", "owner");

        assertTrue(result.canPublish());
        assertEquals(1, result.getCitations().size());
        assertEquals("The database migration coordinates token rotation and recovery.",
                result.getCitations().get(0).getContent());
    }

    @Test
    void doesNotExceedFiveCitationsWhenTheGoalHasMoreThanFiveDistinctTopics() {
        RagVersionEntity version = readyVersion("version-1", "dataset-1", "owner");
        List<String> topicEvidence = List.of(
                "The database migration has a rollback plan.",
                "Token rotation follows the security checklist.",
                "Session recovery restores the account state.",
                "Cache refresh invalidates stale entries.",
                "Queue restart resumes pending jobs.",
                "Permission audit records role changes.");
        List<RagVersionDocumentEntity> documents = topicEvidence.stream().map((content) ->
                versionDocument("version-1", "file-1", "doc-" + content.hashCode(), content, 1)).toList();
        KnowledgeSnapshotRetrievalService service = retrievalService(version, documents,
                List.of(versionFile("file-1", "operations.md")));

        KnowledgeRetrievalResult result = service.retrieve("version-1",
                "database migration, token rotation, session recovery, cache refresh, queue restart, permission audit",
                "owner");

        assertFalse(result.canPublish());
        assertTrue(result.getCitations().isEmpty());
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

    private static KnowledgeSnapshotRetrievalService retrievalService(RagVersionEntity version,
            List<RagVersionDocumentEntity> snapshotDocuments, List<RagVersionFileEntity> snapshotFiles) {
        RagVersionRepository versionRepository = proxy(RagVersionRepository.class,
                (method, args) -> method.getName().equals("selectOne") ? version : defaultValue(method.getReturnType()));
        RagVersionDocumentRepository documentRepository = proxy(RagVersionDocumentRepository.class,
                (method, args) -> method.getName().equals("selectList") ? snapshotDocuments
                        : defaultValue(method.getReturnType()));
        RagVersionFileRepository fileRepository = proxy(RagVersionFileRepository.class,
                (method, args) -> method.getName().equals("selectList") ? snapshotFiles
                        : defaultValue(method.getReturnType()));
        return new KnowledgeSnapshotRetrievalService(versionRepository, documentRepository, fileRepository);
    }

    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (ignored, method, args) -> handler.apply(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

}
