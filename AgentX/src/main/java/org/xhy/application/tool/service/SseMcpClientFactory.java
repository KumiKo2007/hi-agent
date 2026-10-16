package org.xhy.application.tool.service;

import java.time.Duration;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.McpUrlProviderService;

/** 迁移期 SSE 连接适配器。请求、响应和密钥都不写入 SDK 日志。 */
@Component
public class SseMcpClientFactory implements McpClientFactory {
    private final McpUrlProviderService urls;

    public SseMcpClientFactory(McpUrlProviderService urls) {
        this.urls = urls;
    }

    @Override
    public McpClient open(String serverName, String userId) {
        String url = urls.getMcpToolUrl(serverName, userId);
        HttpMcpTransport transport = new HttpMcpTransport.Builder().sseUrl(url).logRequests(false).logResponses(false)
                .timeout(Duration.ofSeconds(60)).build();
        return new DefaultMcpClient.Builder().transport(transport).build();
    }
}
