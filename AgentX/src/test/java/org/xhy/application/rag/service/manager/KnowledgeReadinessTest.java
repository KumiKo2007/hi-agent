package org.xhy.application.rag.service.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xhy.application.rag.dto.KnowledgeReadinessDTO;
import org.xhy.domain.rag.constant.FileProcessingStatusEnum;
import org.xhy.domain.rag.model.FileDetailEntity;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.service.DocumentUnitDomainService;
import org.xhy.domain.rag.service.EmbeddingDomainService;
import org.xhy.domain.rag.service.FileDetailDomainService;
import org.xhy.domain.rag.service.RagQaDatasetDomainService;
import org.xhy.domain.rag.service.management.RagDataAccessDomainService;
import org.xhy.domain.rag.service.management.RagVersionDomainService;
import org.xhy.domain.rag.service.management.UserRagDomainService;
import org.xhy.infrastructure.mq.core.MessagePublisher;
import org.xhy.infrastructure.rag.service.UserModelConfigResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识就绪状态的应用接口测试。 */
class KnowledgeReadinessTest {

    private static final String DATASET_ID = "dataset-1";
    private static final String USER_ID = "user-1";

    private FileDetailDomainService files;
    private RagVersionDomainService versions;
    private RagQaDatasetAppService service;

    @BeforeEach
    void setUp() {
        RagQaDatasetDomainService datasets = mock(RagQaDatasetDomainService.class);
        files = mock(FileDetailDomainService.class);
        versions = mock(RagVersionDomainService.class);
        service = new RagQaDatasetAppService(datasets, files, mock(DocumentUnitDomainService.class),
                mock(MessagePublisher.class), mock(EmbeddingDomainService.class), mock(RagPublishAppService.class),
                versions, mock(UserRagDomainService.class), mock(RagDataAccessDomainService.class),
                mock(UserModelConfigResolver.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @Test
    void should_reportPendingFileWithoutSearchableVersion() {
        when(files.listAllFilesByDataset(DATASET_ID, USER_ID)).thenReturn(List.of(file("file-1", 1)));

        KnowledgeReadinessDTO readiness = service.getKnowledgeReadiness(DATASET_ID, USER_ID);

        assertEquals("PROCESSING", readiness.getStatus());
        assertEquals(0, readiness.getReadyFileCount());
        assertNull(readiness.getLatestReadyVersionId());
        assertThrows(RuntimeException.class, () -> service.createReadyKnowledgeVersion(DATASET_ID, USER_ID));
        verify(versions, never()).createReadyRagVersionSnapshot(eq(DATASET_ID), anyString(), eq(USER_ID));
    }

    @Test
    void should_reportProcessingFailureAndReason() {
        FileDetailEntity failed = file("file-2", FileProcessingStatusEnum.OCR_FAILED.getCode());
        failed.setProcessingError("无法识别文件内容，请检查格式后重试");
        when(files.listAllFilesByDataset(DATASET_ID, USER_ID)).thenReturn(List.of(failed));

        KnowledgeReadinessDTO readiness = service.getKnowledgeReadiness(DATASET_ID, USER_ID);

        assertEquals("FAILED", readiness.getStatus());
        assertEquals("无法识别文件内容，请检查格式后重试", readiness.getFailureReason());
        assertNull(readiness.getLatestReadyVersionId());
    }

    @Test
    void should_createStableReadyVersionOnlyWhenEveryFileIsComplete() {
        when(files.listAllFilesByDataset(DATASET_ID, USER_ID)).thenReturn(List.of(file("file-3", 4)));
        RagVersionEntity created = new RagVersionEntity();
        created.setId("ready-version-123");
        created.setVersion("1.0.1");
        created.setFileCount(1);
        created.setDocumentCount(2);
        when(versions.createReadyRagVersionSnapshot(eq(DATASET_ID), anyString(), eq(USER_ID))).thenReturn(created);
        when(versions.findLatestReadyVersion(DATASET_ID, USER_ID)).thenReturn(created);

        String versionId = service.createReadyKnowledgeVersion(DATASET_ID, USER_ID).getId();
        KnowledgeReadinessDTO readiness = service.getKnowledgeReadiness(DATASET_ID, USER_ID);

        assertEquals("ready-version-123", versionId);
        assertEquals("READY", readiness.getStatus());
        assertEquals("ready-version-123", readiness.getLatestReadyVersionId());
        assertEquals(1, readiness.getReadyFileCount());
    }

    private FileDetailEntity file(String id, int status) {
        FileDetailEntity entity = new FileDetailEntity();
        entity.setId(id);
        entity.setProcessingStatus(status);
        return entity;
    }
}
