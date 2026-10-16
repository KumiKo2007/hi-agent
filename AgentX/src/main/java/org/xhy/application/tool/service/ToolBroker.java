package org.xhy.application.tool.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

/** 模型提出动作后唯一允许调用 MCP 的边界。 */
@Service
public class ToolBroker {
    private static final Logger logger = LoggerFactory.getLogger(ToolBroker.class);

    private final ToolActionCatalog catalog;
    private final McpClientFactory clients;

    public ToolBroker(ToolActionCatalog catalog, McpClientFactory clients) {
        this.catalog = catalog;
        this.clients = clients;
    }

    /** 只读动作先校验登记、分类、参数与远端实际结构，然后才调用 MCP。 */
    public String executeReadOnly(AgentEntity agent, String userId, String serviceId, String actionName,
            String arguments) {
        RegisteredToolAction action = catalog.require(agent, userId, serviceId, actionName);
        if (action.classification() != ToolActionClassification.READ_ONLY) {
            throw new BusinessException("外部写动作需要审批，不能通过只读边界执行");
        }
        Map<String, Object> schema = action.schema();
        String validatedArguments = McpSchema.validateArguments(schema, arguments);
        try (McpClient client = clients.open(action.serverName(), userId)) {
            ToolSpecification live = client.listTools().stream().filter(spec -> actionName.equals(spec.name()))
                    .findFirst().orElseThrow(() -> new BusinessException("MCP 服务未提供已登记的工具动作"));
            if (!schema.equals(McpSchema.fromRuntime(live.parameters()))) {
                throw new BusinessException("MCP 工具动作结构已变化，执行已暂停");
            }
            String result = client
                    .executeTool(ToolExecutionRequest.builder().name(actionName).arguments(validatedArguments).build());
            logger.info("只读工具动作执行完成: serviceId={}, action={}", serviceId, actionName);
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("MCP 工具动作执行失败");
        }
    }
}
