package org.xhy.interfaces.api.portal.agent;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.xhy.interfaces.dto.run.request.DecideRunApprovalRequest;
import org.xhy.interfaces.dto.run.request.ResolveRunReconciliationRequest;

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
        return Result.success(runs.start(sessionId, request.getGoal(), UserContext.getCurrentUserId(),
                request.getApprovalPolicy(), request.getKnowledgeBaseId(), request.getTargetRepository()));
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

    /** 取消当前使用者拥有的未完成运行。 */
    @DeleteMapping("/runs/{runId}")
    public Result<RunDTO> cancel(@PathVariable String runId) {
        return Result.success(runs.cancel(runId, UserContext.getCurrentUserId()));
    }

    /** 动作式别名，供 Web 客户端和旧调用方使用。 */
    @PostMapping("/runs/{runId}/cancel")
    public Result<RunDTO> cancelByAction(@PathVariable String runId) {
        return Result.success(runs.cancel(runId, UserContext.getCurrentUserId()));
    }

    /** Only the run owner can approve or reject its pending fixed write action. */
    @PostMapping("/runs/{runId}/approvals/{approvalId}")
    public Result<RunDTO> decideApproval(@PathVariable String runId, @PathVariable String approvalId,
            @RequestBody @Validated DecideRunApprovalRequest request) {
        boolean approve = request.getDecision() == DecideRunApprovalRequest.Decision.APPROVE;
        return Result.success(runs.decideApproval(runId, approvalId, UserContext.getCurrentUserId(), approve));
    }

    /** Only the run owner may resolve an uncertain approved write, with an auditable evidence record. */
    @PostMapping("/runs/{runId}/approvals/{approvalId}/reconciliation")
    public Result<RunDTO> resolveReconciliation(@PathVariable String runId, @PathVariable String approvalId,
            @RequestBody @Validated ResolveRunReconciliationRequest request) {
        return Result.success(runs.resolveReconciliation(runId, approvalId, UserContext.getCurrentUserId(),
                request.getOutcome().name(), request.getEvidence(), request.getEvidenceReference(),
                request.getExternalResourceId()));
    }
}
