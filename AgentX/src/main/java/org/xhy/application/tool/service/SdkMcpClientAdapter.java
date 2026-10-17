package org.xhy.application.tool.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.transport.PresetParameter;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

/** Adapts the official synchronous Java MCP client to the existing broker-facing contract. */
final class SdkMcpClientAdapter implements McpClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String clientKey;
    private final McpSyncClient client;

    SdkMcpClientAdapter(String clientKey, McpSyncClient client) {
        this.clientKey = clientKey;
        this.client = client;
    }

    @Override
    public String key() {
        return clientKey;
    }

    @Override
    public List<ToolSpecification> listTools() {
        return client.listTools().tools().stream().map(McpSdkToolMapper::toToolSpecification).toList();
    }

    @Override
    public String executeTool(ToolExecutionRequest request) {
        try {
            Map<String, Object> arguments = JSON.readValue(request.arguments(), new TypeReference<>() {
            });
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(request.name(), arguments));
            if (Boolean.TRUE.equals(result.isError())) {
                throw new McpToolExecutionException();
            }
            if (result.structuredContent() != null) {
                return JSON.writeValueAsString(result.structuredContent());
            }
            if (result.content() == null) {
                return "";
            }
            return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast).map(McpSchema.TextContent::text)
                    .collect(Collectors.joining("\n"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("MCP 工具参数不是有效 JSON", exception);
        }
    }

    @Override
    public List<McpResource> listResources() {
        throw unsupported();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        throw unsupported();
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        throw unsupported();
    }

    @Override
    public List<McpPrompt> listPrompts() {
        throw unsupported();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        throw unsupported();
    }

    @Override
    public void checkHealth() {
        client.ping();
    }

    @Override
    public void presetParameters(List<PresetParameter> parameters) {
        throw unsupported();
    }

    @Override
    public void close() {
        client.closeGracefully();
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("MCP SDK adapter exposes tools only through the action broker");
    }
}
