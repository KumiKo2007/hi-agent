package org.xhy.application.conversation.service.message.agent;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.handler.context.ChatContext;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.application.tool.service.McpSchema;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

/** 将旧聊天可见的 MCP 动作限制为平台审定的只读动作。 */
@Component
public class AgentToolManager {
    private final ToolActionCatalog catalog;
    private final ToolBroker broker;
    private final McpClientFactory clients;

    public AgentToolManager(ToolActionCatalog catalog, ToolBroker broker, McpClientFactory clients) {
        this.catalog = catalog;
        this.broker = broker;
        this.clients = clients;
    }

    /** 只公开已选 MCP 服务中的只读动作；每次执行仍由 Broker 重新校验。 */
    public ToolProvider createToolProvider(ChatContext context) {
        if (context == null || context.getAgent() == null || context.getAgent().getToolIds().isEmpty()) {
            return null;
        }
        return request -> {
            Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
            for (RegisteredToolAction action : catalog.list(context.getAgent(), context.getUserId())) {
                if (action.classification() != ToolActionClassification.READ_ONLY || action.schema() == null) {
                    continue;
                }
                ToolSpecification live;
                try (McpClient client = clients.open(action.serverName(), context.getUserId())) {
                    live = client.listTools().stream().filter(spec -> action.name().equals(spec.name())).findFirst()
                            .orElseThrow(() -> new BusinessException("MCP 服务未提供已登记的工具动作"));
                    if (!action.schema().equals(McpSchema.fromRuntime(live.parameters()))) {
                        throw new BusinessException("MCP 工具动作结构已变化，执行已暂停");
                    }
                } catch (BusinessException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new BusinessException("MCP 工具动作目录读取失败");
                }
                String alias = action.serverName() + "__" + action.name();
                String description = action.description() == null ? "已登记的只读工具动作" : action.description();
                ToolSpecification safeSpec = ToolSpecification.builder().name(alias).description(description)
                        .parameters(live.parameters()).build();
                tools.put(safeSpec, (toolRequest, memoryId) -> broker.executeReadOnly(context.getAgent(),
                        context.getUserId(), action.serviceId(), action.name(), toolRequest.arguments()));
            }
            return new ToolProviderResult(tools);
        };
    }
}
