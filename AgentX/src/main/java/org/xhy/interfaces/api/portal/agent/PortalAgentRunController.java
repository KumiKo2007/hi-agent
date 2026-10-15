package org.xhy.interfaces.api.portal.agent;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.application.run.service.RunAppService;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;
import org.xhy.interfaces.dto.run.request.StartRunRequest;

import java.util.List;

/** 使用者的目标运行接口。 */
@RestController
@RequestMapping("/agents")
public class PortalAgentRunController {
    private final RunAppService runs;

    public PortalAgentRunController(RunAppService runs) {
        this.runs = runs;
    }

    /** 在已有会话中提交一个独立目标。 */
    @PostMapping("/sessions/{sessionId}/runs")
    public Result<RunDTO> start(@PathVariable String sessionId, @RequestBody @Validated StartRunRequest request) {
        return Result.success(runs.start(sessionId, request.getGoal(), UserContext.getCurrentUserId()));
    }

    /** 获取当前使用者在会话中的运行列表。 */
    @GetMapping("/sessions/{sessionId}/runs")
    public Result<List<RunDTO>> list(@PathVariable String sessionId) {
        return Result.success(runs.list(sessionId, UserContext.getCurrentUserId()));
    }

    /** 获取运行状态、结果和步骤。 */
    @GetMapping("/runs/{runId}")
    public Result<RunDTO> detail(@PathVariable String runId) {
        return Result.success(runs.detail(runId, UserContext.getCurrentUserId()));
    }
}
