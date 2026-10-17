package org.xhy.domain.tool.repository;

import org.xhy.domain.tool.model.ToolActionPolicy;

/** 平台管理的 MCP 动作策略存储。 */
public interface ToolActionPolicyStore {
    ToolActionPolicy find(String serviceId, String actionName);

    void save(ToolActionPolicy policy);
}
