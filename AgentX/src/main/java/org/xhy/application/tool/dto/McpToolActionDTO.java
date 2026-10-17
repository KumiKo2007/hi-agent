package org.xhy.application.tool.dto;

import java.util.Map;

import org.xhy.domain.tool.constant.ToolActionClassification;

/** 单个 MCP 服务内的工具动作及平台分类。 */
public record McpToolActionDTO(String serviceId, String serverName, String name, String description,
        Map<String, Object> parameters, ToolActionClassification classification, boolean explicitlyClassified) {
    public McpToolActionDTO(String serviceId, String serverName, String name, String description,
            Map<String, Object> parameters, ToolActionClassification classification) {
        this(serviceId, serverName, name, description, parameters, classification, classification != null);
    }
}
