package org.xhy.domain.tool.constant;

/** MCP wire transport selected for a tool service. */
public enum McpTransportType {
    STREAMABLE_HTTP, LEGACY_SSE;

    /** New services use Streamable HTTP; rows created before transport selection are migrated to legacy SSE. */
    public static McpTransportType defaultForNewService() {
        return STREAMABLE_HTTP;
    }

    public String migrationState() {
        return this == STREAMABLE_HTTP ? "CURRENT" : "MIGRATION_REQUIRED";
    }
}
