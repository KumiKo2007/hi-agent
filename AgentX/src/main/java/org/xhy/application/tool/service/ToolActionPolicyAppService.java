package org.xhy.application.tool.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.xhy.application.tool.dto.McpToolActionDTO;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.domain.tool.model.ToolActionPolicy;
import org.xhy.domain.tool.model.ToolEntity;
import org.xhy.domain.tool.model.UserToolEntity;
import org.xhy.domain.tool.model.config.ToolDefinition;
import org.xhy.domain.tool.repository.ToolActionPolicyStore;
import org.xhy.domain.tool.repository.AgentToolActionSelectionStore;
import org.xhy.domain.tool.service.ToolDomainService;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.infrastructure.exception.BusinessException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

/** 公开展示服务内动作，平台管理员维护动作的安全分类。 */
@Service
public class ToolActionPolicyAppService {
    private final UserToolDomainService installedTools;
    private final ToolDomainService services;
    private final ToolActionPolicyStore policies;
    private final McpClientFactory clients;
    private final AgentToolActionSelectionStore selections;

    public ToolActionPolicyAppService(UserToolDomainService installedTools, ToolDomainService services,
            ToolActionPolicyStore policies, McpClientFactory clients, AgentToolActionSelectionStore selections) {
        this.installedTools = installedTools;
        this.services = services;
        this.policies = policies;
        this.clients = clients;
        this.selections = selections;
    }

    /** 使用者查看已安装 MCP 服务及其中每个动作的分类。 */
    public List<McpToolActionDTO> listInstalledActions(String serviceId, String userId) {
        UserToolEntity installed = installedTools.findByToolIdAndUserId(serviceId, userId);
        if (installed == null) {
            throw new BusinessException("使用者未安装该 MCP 服务");
        }
        List<McpToolActionDTO> result = new ArrayList<>();
        if (installed.getToolList() == null) {
            return result;
        }
        for (ToolDefinition definition : installed.getToolList()) {
            ToolActionPolicy policy = policies.find(serviceId, definition.getName());
            result.add(new McpToolActionDTO(serviceId, installed.getMcpServerName(), definition.getName(),
                    definition.getDescription(), policy == null ? definition.getParameters() : policy.schema(),
                    policy == null ? ToolActionClassification.WRITE_REQUIRES_APPROVAL : policy.classification(),
                    policy != null));
        }
        return result;
    }

    /** 平台管理员查看 MCP 服务及其动作分类。 */
    public List<McpToolActionDTO> listForReview(String serviceId) {
        ToolEntity service = services.getTool(serviceId);
        List<McpToolActionDTO> result = new ArrayList<>();
        if (service.getToolList() == null) {
            return result;
        }
        for (ToolDefinition definition : service.getToolList()) {
            ToolActionPolicy policy = policies.find(serviceId, definition.getName());
            result.add(new McpToolActionDTO(serviceId, service.getMcpServerName(), definition.getName(),
                    definition.getDescription(), policy == null ? definition.getParameters() : policy.schema(),
                    policy == null ? ToolActionClassification.WRITE_REQUIRES_APPROVAL : policy.classification(),
                    policy != null));
        }
        return result;
    }

    /** 管理员审定动作类别和固定结构；需要真实运行时再次和 MCP 服务核对。 */
    public McpToolActionDTO classify(String serviceId, String actionName, ToolActionClassification classification) {
        ToolEntity service = services.getTool(serviceId);
        if (service.getToolList() == null || service.getToolList().stream().noneMatch(
                action -> actionName.equals(action.getName()) && !Boolean.FALSE.equals(action.getEnabled()))) {
            throw new BusinessException("MCP 服务未登记该工具动作");
        }
        if (classification == null) {
            throw new BusinessException("工具动作分类无效");
        }
        Map<String, Object> schema;
        try (McpClient client = clients.open(service.getMcpServerName(), service.getUserId())) {
            ToolSpecification live = client.listTools().stream().filter(action -> actionName.equals(action.name()))
                    .findFirst().orElseThrow(() -> new BusinessException("MCP 服务未提供该动作"));
            schema = McpSchema.fromRuntime(live.parameters());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("无法确认 MCP 工具动作结构");
        }
        ToolActionPolicy policy = new ToolActionPolicy(serviceId, actionName, classification, schema);
        policies.save(policy);
        selections.revokePreauthorization(serviceId, actionName);
        ToolDefinition definition = service.getToolList().stream().filter(item -> actionName.equals(item.getName()))
                .findFirst().orElseThrow();
        return new McpToolActionDTO(serviceId, service.getMcpServerName(), actionName, definition.getDescription(),
                schema, classification, true);
    }
}
