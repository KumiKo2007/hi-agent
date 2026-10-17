package org.xhy.application.tool.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.domain.tool.model.ToolActionPolicy;
import org.xhy.domain.tool.model.UserToolEntity;
import org.xhy.domain.tool.model.config.ToolDefinition;
import org.xhy.domain.tool.repository.ToolActionPolicyStore;
import org.xhy.domain.tool.repository.AgentToolActionSelectionStore;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.infrastructure.exception.BusinessException;

/** 将 Agent 允许的已安装 MCP 服务与平台动作策略组合成可执行目录。 */
@Service
public class PersistentToolActionCatalog implements ToolActionCatalog {
    private final UserToolDomainService installedTools;
    private final ToolActionPolicyStore policies;
    private final AgentToolActionSelectionStore selections;

    public PersistentToolActionCatalog(UserToolDomainService installedTools, ToolActionPolicyStore policies,
            AgentToolActionSelectionStore selections) {
        this.installedTools = installedTools;
        this.policies = policies;
        this.selections = selections;
    }

    @Override
    public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
        if (agent == null || agent.getToolIds() == null || !agent.getToolIds().contains(serviceId)) {
            throw new BusinessException("Agent 未允许该 MCP 服务");
        }
        UserToolEntity installed = installedTools.findByToolIdAndUserId(serviceId, userId);
        if (installed == null) {
            throw new BusinessException("使用者未安装该 MCP 服务");
        }
        if (installed.getToolList() == null) {
            throw new BusinessException("MCP 服务尚无已登记的工具动作");
        }
        ToolDefinition definition = installed.getToolList().stream()
                .filter(tool -> actionName.equals(tool.getName()) && !Boolean.FALSE.equals(tool.getEnabled()))
                .findFirst().orElseThrow(() -> new BusinessException("MCP 工具动作未登记或已禁用"));
        if (!selections.contains(agent.getId(), serviceId, actionName)) {
            throw new BusinessException("Agent 未允许该工具动作");
        }
        ToolActionPolicy policy = policies.find(serviceId, actionName);
        ToolActionClassification classification = policy == null
                ? ToolActionClassification.WRITE_REQUIRES_APPROVAL
                : policy.classification();
        Map<String, Object> schema = policy == null ? definition.getParameters() : policy.schema();
        return new RegisteredToolAction(serviceId, installed.getMcpServerName(), actionName,
                definition.getDescription(), schema, classification, policy != null,
                policy != null && classification == ToolActionClassification.WRITE_REQUIRES_APPROVAL
                        && selections.isPreauthorized(agent.getId(), serviceId, actionName));
    }

    @Override
    public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
        if (agent == null || agent.getToolIds() == null || agent.getToolIds().isEmpty()) {
            return List.of();
        }
        List<RegisteredToolAction> result = new ArrayList<>();
        for (UserToolEntity installed : installedTools.getUserToolsByIds(userId, agent.getToolIds())) {
            if (installed.getToolList() == null) {
                continue;
            }
            for (ToolDefinition definition : installed.getToolList()) {
                if (!Boolean.FALSE.equals(definition.getEnabled())
                        && selections.contains(agent.getId(), installed.getToolId(), definition.getName())) {
                    result.add(require(agent, userId, installed.getToolId(), definition.getName()));
                }
            }
        }
        return result;
    }
}
