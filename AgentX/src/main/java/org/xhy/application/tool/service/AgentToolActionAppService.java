package org.xhy.application.tool.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.tool.model.UserToolEntity;
import org.xhy.domain.tool.repository.AgentToolActionSelectionStore;
import org.xhy.domain.tool.repository.ToolActionPolicyStore;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.infrastructure.exception.BusinessException;

/** Agent 创建者管理每个 MCP 服务中可提出的具体动作。 */
@Service
public class AgentToolActionAppService {
    private final AgentDomainService agents;
    private final UserToolDomainService installedTools;
    private final AgentToolActionSelectionStore selections;
    private final ToolActionPolicyStore policies;

    public AgentToolActionAppService(AgentDomainService agents, UserToolDomainService installedTools,
            AgentToolActionSelectionStore selections, ToolActionPolicyStore policies) {
        this.agents = agents;
        this.installedTools = installedTools;
        this.selections = selections;
        this.policies = policies;
    }

    /** 获取创建者为一个服务启用的动作名称。 */
    public List<String> list(String agentId, String serviceId, String userId) {
        requireSelectedService(agentId, serviceId, userId);
        return selections.list(agentId, serviceId);
    }

    /** 覆盖一个服务的动作允许列表；空列表表示全部关闭。 */
    public List<String> replace(String agentId, String serviceId, List<String> actionNames, String userId) {
        UserToolEntity installed = requireSelectedService(agentId, serviceId, userId);
        if (actionNames == null || actionNames.stream().anyMatch(name -> name == null || name.isBlank())
                || actionNames.size() != actionNames.stream().distinct().count() || installed.getToolList() == null
                || actionNames.stream()
                        .anyMatch(name -> installed.getToolList().stream()
                                .noneMatch(definition -> name.equals(definition.getName())
                                        && !Boolean.FALSE.equals(definition.getEnabled())))) {
            throw new BusinessException("包含未登记或重复的 MCP 工具动作");
        }
        selections.replace(agentId, serviceId, actionNames);
        return selections.list(agentId, serviceId);
    }

    /** Lists write actions the creator has allowed to execute without a run-time prompt. */
    public List<String> listPreauthorized(String agentId, String serviceId, String userId) {
        requireSelectedService(agentId, serviceId, userId);
        return selections.preauthorized(agentId, serviceId);
    }

    /** Replaces creator preauthorizations for selected, explicitly classified write actions. */
    public List<String> replacePreauthorized(String agentId, String serviceId, List<String> actionNames,
            String userId) {
        UserToolEntity installed = requireSelectedService(agentId, serviceId, userId);
        List<String> requested = actionNames == null ? List.of() : actionNames;
        if (requested.stream().anyMatch(name -> name == null || name.isBlank())
                || requested.size() != requested.stream().distinct().count()) {
            throw new BusinessException("包含重复或无效的预授权动作");
        }
        for (String actionName : requested) {
            boolean enabled = installed.getToolList() != null
                    && installed.getToolList().stream().anyMatch(definition -> actionName.equals(definition.getName())
                            && !Boolean.FALSE.equals(definition.getEnabled()));
            if (!enabled || !selections.contains(agentId, serviceId, actionName)) {
                throw new BusinessException("只能预授权 Agent 已启用的 MCP 动作");
            }
            var policy = policies.find(serviceId, actionName);
            if (policy == null || policy.classification() != ToolActionClassification.WRITE_REQUIRES_APPROVAL) {
                throw new BusinessException("只能预授权已明确分类的外部写动作");
            }
        }
        selections.replacePreauthorized(agentId, serviceId, requested);
        return selections.preauthorized(agentId, serviceId);
    }

    private UserToolEntity requireSelectedService(String agentId, String serviceId, String userId) {
        AgentEntity agent = agents.getAgent(agentId, userId);
        if (!agent.getToolIds().contains(serviceId)) {
            throw new BusinessException("Agent 未选择该 MCP 服务");
        }
        UserToolEntity installed = installedTools.findByToolIdAndUserId(serviceId, userId);
        if (installed == null) {
            throw new BusinessException("创建者未安装该 MCP 服务");
        }
        return installed;
    }
}
