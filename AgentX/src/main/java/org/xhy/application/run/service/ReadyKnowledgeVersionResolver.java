package org.xhy.application.run.service;

import org.springframework.stereotype.Service;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.service.management.RagVersionDomainService;
import org.xhy.infrastructure.exception.BusinessException;

/** Pins the latest ready snapshot for the first knowledge base configured on an agent. */
@Service
public class ReadyKnowledgeVersionResolver implements RunKnowledgeVersionResolver {

    private final AgentDomainService agents;
    private final RagVersionDomainService versions;

    public ReadyKnowledgeVersionResolver(AgentDomainService agents, RagVersionDomainService versions) {
        this.agents = agents;
        this.versions = versions;
    }

    @Override
    public String resolve(String agentId, String userId) {
        // The session only gives us an agent ID. Re-check ownership before reading its knowledge configuration.
        AgentEntity agent = agents.getAgentWithPermissionCheck(agentId, userId);
        if (agent == null || agent.getKnowledgeBaseIds() == null) {
            return null;
        }
        for (String datasetId : agent.getKnowledgeBaseIds()) {
            if (datasetId == null || datasetId.isBlank()) {
                continue;
            }
            RagVersionEntity version = versions.findLatestReadyVersion(datasetId, userId);
            if (version != null && Boolean.TRUE.equals(version.getSearchReady())) {
                return version.getId();
            }
        }
        return null;
    }

    @Override
    public String resolve(String agentId, String userId, String knowledgeBaseId) {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            return resolve(agentId, userId);
        }
        AgentEntity agent = agents.getAgentWithPermissionCheck(agentId, userId);
        if (agent == null || agent.getKnowledgeBaseIds() == null
                || !agent.getKnowledgeBaseIds().contains(knowledgeBaseId)) {
            throw new BusinessException("所选知识库未配置给当前助理");
        }
        RagVersionEntity version = versions.findLatestReadyVersion(knowledgeBaseId, userId);
        if (version == null || !Boolean.TRUE.equals(version.getSearchReady())) {
            throw new BusinessException("所选知识库尚无可检索版本");
        }
        return version.getId();
    }
}
