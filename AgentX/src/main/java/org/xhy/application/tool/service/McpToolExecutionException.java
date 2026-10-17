package org.xhy.application.tool.service;

/** Safe signal for a tool-level MCP error; remote error details may contain private data. */
public final class McpToolExecutionException extends RuntimeException {
    public McpToolExecutionException() {
        super("MCP 服务返回工具错误");
    }
}
