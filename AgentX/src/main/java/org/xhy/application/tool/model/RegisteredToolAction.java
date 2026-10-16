package org.xhy.application.tool.model;

import java.util.Map;

import org.xhy.domain.tool.constant.ToolActionClassification;

/** 已安装 MCP 服务中由平台登记的单个动作。 */
public record RegisteredToolAction(String serviceId, String serverName, String name, String description,
        Map<String, Object> schema, ToolActionClassification classification) {
}
