package org.xhy.application.run.service;

import org.springframework.stereotype.Component;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.application.tool.model.McpActionResult;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.model.AgentVersionEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalPolicy;
import org.xhy.infrastructure.exception.BusinessException;

/** Rechecks the selected target and forwards an immutable approval through the generic MCP boundary. */
@Component
public class McpApprovedRunActionExecutor implements ApprovedRunActionExecutor {
    private final AgentDomainService agents;
    private final RunTargetRepositoryAuthorization targetRepositories;
    private final ToolBroker tools;
    private final ApprovedActionResultNormalizer resultNormalizer;

    public McpApprovedRunActionExecutor(AgentDomainService agents, RunTargetRepositoryAuthorization targetRepositories,
            ToolBroker tools, ApprovedActionResultNormalizer resultNormalizer) {
        this.agents = agents;
        this.targetRepositories = targetRepositories;
        this.tools = tools;
        this.resultNormalizer = resultNormalizer;
    }

    @Override
    public String execute(RunEntity run, RunApprovalEntity approval) {
        if (approval == null || !"APPROVED".equals(approval.getStatus())) {
            throw new BusinessException("外部写动作尚未获得使用者批准");
        }
        AgentEntity agent = agents.getAgentById(run.getAgentId());
        if (!run.getUserId().equals(agent.getUserId()) || !Boolean.TRUE.equals(agent.getEnabled())) {
            throw new BusinessException("助理已被禁用或不属于当前使用者");
        }
        AgentEntity runAgent = agent;
        if (run.getAgentVersionId() != null && !run.getAgentVersionId().isBlank()) {
            AgentVersionEntity version = agents.getAgentVersionById(run.getAgentVersionId());
            if (version == null || !run.getAgentId().equals(version.getAgentId())) {
                throw new BusinessException("固定 Agent 版本不存在，已批准动作已取消");
            }
            runAgent = new AgentEntity();
            runAgent.setId(agent.getId());
            runAgent.setUserId(agent.getUserId());
            runAgent.setEnabled(agent.getEnabled());
            runAgent.setToolIds(version.getToolIds());
        }
        if ("CREATOR_PREAUTHORIZATION".equals(approval.getApprovalSource())) {
            if (RunApprovalPolicy.fromValue(run.getApprovalPolicy()).requiresApproval(true)
                    || !tools.hasCurrentCreatorPreauthorization(runAgent, run.getUserId(), approval.getServiceId(),
                            approval.getActionName())) {
                throw new BusinessException("创建者预授权已撤销或当前运行要求逐次审批");
            }
        }
        if (approval.getTargetRepository() != null && !approval.getTargetRepository().isBlank()) {
            targetRepositories.revalidate(run.getUserId(), approval.getTargetRepository());
        }
        McpActionResult result = tools.executeApprovedWrite(runAgent, run.getUserId(), approval, () -> {
            if (approval.getTargetRepository() != null && !approval.getTargetRepository().isBlank()) {
                targetRepositories.revalidate(run.getUserId(), approval.getTargetRepository());
            }
        });
        return resultNormalizer.normalize(result.serverName(), result.actionName(), result.targetRepository(),
                result.resultPayload());
    }
}
