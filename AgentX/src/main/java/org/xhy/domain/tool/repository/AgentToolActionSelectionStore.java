package org.xhy.domain.tool.repository;

import java.util.List;

/** Agent 创建者显式启用的 MCP 工具动作。 */
public interface AgentToolActionSelectionStore {
    List<String> list(String agentId, String serviceId);

    boolean contains(String agentId, String serviceId, String actionName);

    void replace(String agentId, String serviceId, List<String> actionNames);
}
