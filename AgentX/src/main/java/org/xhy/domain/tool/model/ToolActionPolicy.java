package org.xhy.domain.tool.model;

import java.util.Map;

import org.xhy.domain.tool.constant.ToolActionClassification;

/** 平台审核后的动作分类和固定参数结构。 */
public record ToolActionPolicy(String serviceId, String actionName, ToolActionClassification classification,
        Map<String, Object> schema) {
}
