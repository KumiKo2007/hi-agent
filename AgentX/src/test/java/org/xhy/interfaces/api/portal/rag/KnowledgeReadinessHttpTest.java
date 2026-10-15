package org.xhy.interfaces.api.portal.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.rag.dto.KnowledgeReadinessDTO;
import org.xhy.application.rag.dto.RagVersionDTO;
import org.xhy.application.rag.service.manager.RagQaDatasetAppService;
import org.xhy.infrastructure.auth.UserContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 知识就绪状态的公开 HTTP 契约。 */
class KnowledgeReadinessHttpTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void should_exposeFailureReasonWithoutReadyVersion() throws Exception {
        UserContext.setCurrentUserId("user-1");
        RagQaDatasetAppService service = mock(RagQaDatasetAppService.class);
        KnowledgeReadinessDTO readiness = new KnowledgeReadinessDTO();
        readiness.setDatasetId("dataset-1");
        readiness.setStatus("FAILED");
        readiness.setFailedFileCount(1);
        readiness.setFailureReason("无法识别文件内容，请检查格式后重试");
        when(service.getKnowledgeReadiness("dataset-1", "user-1")).thenReturn(readiness);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RagQaDatasetController(service)).build();

        mvc.perform(get("/rag/datasets/dataset-1/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("无法识别文件内容，请检查格式后重试"))
                .andExpect(jsonPath("$.data.latestReadyVersionId").isEmpty());
    }

    @Test
    void should_returnStableVersionIdentifierAfterConfirmation() throws Exception {
        UserContext.setCurrentUserId("user-1");
        RagQaDatasetAppService service = mock(RagQaDatasetAppService.class);
        RagVersionDTO version = new RagVersionDTO();
        version.setId("ready-version-123");
        version.setVersion("1.0.1");
        version.setSearchReady(true);
        when(service.createReadyKnowledgeVersion("dataset-1", "user-1")).thenReturn(version);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RagQaDatasetController(service)).build();

        mvc.perform(post("/rag/datasets/dataset-1/ready-versions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ready-version-123"))
                .andExpect(jsonPath("$.data.searchReady").value(true));
    }
}
