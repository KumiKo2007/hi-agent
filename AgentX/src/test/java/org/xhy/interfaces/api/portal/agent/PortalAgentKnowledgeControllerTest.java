package org.xhy.interfaces.api.portal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.run.service.KnowledgeCitation;
import org.xhy.application.run.service.KnowledgeRetrievalResult;
import org.xhy.application.run.service.KnowledgeSnapshotRetrievalService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.auth.UserContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Public run knowledge endpoints expose citations from the run's pinned version. */
class PortalAgentKnowledgeControllerTest {

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldReturnCitationsAndKeepNoEvidencePublishableFalse() throws Exception {
        RunStore runs = mock(RunStore.class);
        KnowledgeSnapshotRetrievalService knowledge = mock(KnowledgeSnapshotRetrievalService.class);
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "find gateway details", "version-1");
        when(runs.findOwned("run-1", "owner")).thenReturn(run);
        KnowledgeCitation citation = new KnowledgeCitation();
        citation.setKnowledgeVersionId("version-1");
        citation.setDocumentId("doc-1");
        citation.setFileId("file-1");
        citation.setFileName("design.md");
        citation.setPage(7);
        citation.setContent("gateway retries");
        citation.setSourceUrl("/rag/datasets/dataset-1/ready-versions/version-1/documents/doc-1");
        when(knowledge.retrieve("version-1", "find gateway details", "owner"))
                .thenReturn(new KnowledgeRetrievalResult("version-1", true, "已从固定知识版本检索到依据", List.of(citation)));

        MockMvc api = MockMvcBuilders.standaloneSetup(new PortalAgentKnowledgeController(runs, knowledge)).build();
        UserContext.setCurrentUserId("owner");

        String response = api.perform(get("/agents/runs/run-1/knowledge").queryParam("query", "find gateway details"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(response).path("data");

        assertEquals("version-1", data.path("knowledgeVersionId").asText());
        assertTrue(data.path("hasEvidence").asBoolean());
        assertTrue(data.path("canPublish").asBoolean());
        assertEquals("doc-1", data.path("citations").get(0).path("documentId").asText());
        assertTrue(data.path("citations").get(0).path("sourceUrl").asText().contains("doc-1"));
        verify(knowledge).retrieve(eq("version-1"), eq("find gateway details"), eq("owner"));
    }
}
