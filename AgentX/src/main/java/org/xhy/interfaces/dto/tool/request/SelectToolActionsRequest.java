package org.xhy.interfaces.dto.tool.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** Agent 创建者为单个 MCP 服务选择动作。 */
public class SelectToolActionsRequest {
    @NotNull
    private List<String> actionNames;

    public List<String> getActionNames() {
        return actionNames;
    }

    public void setActionNames(List<String> actionNames) {
        this.actionNames = actionNames;
    }
}
