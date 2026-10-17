package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.service.management.RagVersionDomainService;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadyKnowledgeVersionResolverTest {
    @Test
    void pinsTheSelectedConfiguredKnowledgeBaseVersion() {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setKnowledgeBaseIds(List.of("kb-1", "kb-2"));
        AtomicReference<String> selectedDataset = new AtomicReference<>();
        AgentDomainService agents = new AgentDomainService(null, null, null, null) {
            @Override
            public AgentEntity getAgentWithPermissionCheck(String agentId, String userId) {
                assertEquals("agent-1", agentId);
                assertEquals("owner", userId);
                return agent;
            }
        };
        RagVersionDomainService versions = new RagVersionDomainService(null, null, null, null, null, null) {
            @Override
            public RagVersionEntity findLatestReadyVersion(String datasetId, String userId) {
                selectedDataset.set(datasetId);
                RagVersionEntity version = new RagVersionEntity();
                version.setId("version-2");
                version.setSearchReady(true);
                return version;
            }
        };
        ReadyKnowledgeVersionResolver resolver = new ReadyKnowledgeVersionResolver(agents, versions);

        assertEquals("version-2", resolver.resolve("agent-1", "owner", "kb-2"));
        assertEquals("kb-2", selectedDataset.get());
    }

    @Test
    void rejectsKnowledgeBaseOutsideTheAgentConfiguration() {
        AgentEntity agent = new AgentEntity();
        agent.setKnowledgeBaseIds(List.of("kb-1"));
        AgentDomainService agents = new AgentDomainService(null, null, null, null) {
            @Override
            public AgentEntity getAgentWithPermissionCheck(String agentId, String userId) {
                return agent;
            }
        };
        RagVersionDomainService versions = new RagVersionDomainService(null, null, null, null, null, null);
        ReadyKnowledgeVersionResolver resolver = new ReadyKnowledgeVersionResolver(agents, versions);

        assertThrows(BusinessException.class, () -> resolver.resolve("agent-1", "owner", "kb-outside"));
    }
}
