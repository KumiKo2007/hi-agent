package org.xhy.interfaces.api.portal.agent;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.application.tool.service.AgentToolActionAppService;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;
import org.xhy.interfaces.dto.tool.request.SelectToolActionsRequest;

/** Agent 内 MCP 服务的逐动作允许列表。 */
@RestController
@RequestMapping("/agents")
public class PortalAgentToolActionController {
    private final AgentToolActionAppService actions;

    public PortalAgentToolActionController(AgentToolActionAppService actions) {
        this.actions = actions;
    }

    /** 查看已选择动作。 */
    @GetMapping("/{agentId}/tool-services/{serviceId}/actions")
    public Result<List<String>> list(@PathVariable String agentId, @PathVariable String serviceId) {
        return Result.success(actions.list(agentId, serviceId, UserContext.getCurrentUserId()));
    }

    /** 设置允许的动作；只允许 Agent 创建者更改。 */
    @PutMapping("/{agentId}/tool-services/{serviceId}/actions")
    public Result<List<String>> replace(@PathVariable String agentId, @PathVariable String serviceId,
            @RequestBody @Validated SelectToolActionsRequest request) {
        return Result
                .success(actions.replace(agentId, serviceId, request.getActionNames(), UserContext.getCurrentUserId()));
    }

    /** 查看创建者为已分类写动作设置的预授权。 */
    @GetMapping("/{agentId}/tool-services/{serviceId}/preauthorizations")
    public Result<List<String>> listPreauthorized(@PathVariable String agentId, @PathVariable String serviceId) {
        return Result.success(actions.listPreauthorized(agentId, serviceId, UserContext.getCurrentUserId()));
    }

    /** 设置预授权；未分类、只读或未启用动作会被拒绝。 */
    @PutMapping("/{agentId}/tool-services/{serviceId}/preauthorizations")
    public Result<List<String>> replacePreauthorized(@PathVariable String agentId, @PathVariable String serviceId,
            @RequestBody @Validated SelectToolActionsRequest request) {
        return Result.success(actions.replacePreauthorized(agentId, serviceId, request.getActionNames(),
                UserContext.getCurrentUserId()));
    }
}
