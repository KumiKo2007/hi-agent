package org.xhy.application.tool.model;

/** Untrusted output from one registered MCP action, annotated with its server and immutable target. */
public record McpActionResult(String serverName, String actionName, String targetRepository, String resultPayload) {
}
