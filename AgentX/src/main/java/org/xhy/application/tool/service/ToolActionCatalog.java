package org.xhy.application.tool.service;

import java.util.List;

import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.agent.model.AgentEntity;

/** 只从已安装、已选中的 MCP 服务读取平台登记的动作。 */
public interface ToolActionCatalog {
    RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName);

    List<RegisteredToolAction> list(AgentEntity agent, String userId);
}
