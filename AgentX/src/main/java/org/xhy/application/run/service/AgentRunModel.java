package org.xhy.application.run.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.model.AgentVersionEntity;
import org.xhy.domain.agent.model.AgentWorkspaceEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.agent.service.AgentWorkspaceDomainService;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.domain.llm.service.LLMDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.llm.LLMServiceFactory;

import java.util.ArrayList;
import java.util.List;

/** 使用 Agent 已配置的 OpenAI 兼容或 Anthropic 模型完成简单目标。 */
@Component
public class AgentRunModel implements RunModel {
    private final AgentDomainService agents;
    private final AgentWorkspaceDomainService workspaces;
    private final LLMDomainService llms;
    private final LLMServiceFactory models;

    public AgentRunModel(AgentDomainService agents, AgentWorkspaceDomainService workspaces, LLMDomainService llms,
            LLMServiceFactory models) {
        this.agents = agents;
        this.workspaces = workspaces;
        this.llms = llms;
        this.models = models;
    }

    @Override
    public String execute(RunEntity run) {
        AgentEntity agent = agents.getAgentById(run.getAgentId());
        if (!run.getUserId().equals(agent.getUserId()) && !Boolean.TRUE.equals(agent.getEnabled())) {
            throw new BusinessException("助理已被禁用");
        }
        AgentVersionEntity pinnedVersion = null;
        if (run.getAgentVersionId() != null && !run.getAgentVersionId().isBlank()) {
            pinnedVersion = agents.getAgentVersionById(run.getAgentVersionId());
            if (pinnedVersion == null || !run.getAgentId().equals(pinnedVersion.getAgentId())) {
                throw new BusinessException("固定 Agent 版本不存在");
            }
        }
        AgentWorkspaceEntity workspace = workspaces.getWorkspace(run.getAgentId(), run.getUserId());
        String modelId = run.getModelId();
        if (modelId == null || modelId.isBlank()) {
            modelId = workspace.getLlmModelConfig().getModelId();
        }
        if (modelId == null || modelId.isBlank()) {
            throw new BusinessException("助理尚未配置模型");
        }
        ModelEntity model = llms.getModelById(modelId);
        model.isActive();
        ProviderEntity provider = llms.getProvider(model.getProviderId());
        provider.isActive();
        provider.isAvailable(run.getUserId());
        if (run.getModelConnectionType() != null
                && !run.getModelConnectionType().equals(provider.getProtocol().name())) {
            throw new BusinessException("模型连接类型已变化，运行暂停");
        }

        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = pinnedVersion == null ? agent.getSystemPrompt() : pinnedVersion.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(run.getGoal()));
        return models.getStrandClient(provider, model).chat(messages).aiMessage().text();
    }
}
