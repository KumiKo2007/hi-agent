package org.xhy.application.run.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xhy.application.run.assembler.RunAssembler;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.domain.agent.model.AgentVersionEntity;
import org.xhy.domain.agent.model.AgentWorkspaceEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.agent.service.AgentWorkspaceDomainService;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.domain.llm.service.LLMDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.model.RunApprovalPolicy;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.domain.run.repository.RunTimelineRepository;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.exception.EntityNotFoundException;

import java.util.List;

/** 每个目标独立创建和读取一次运行。 */
@Service
public class RunAppService {
    private static final Logger logger = LoggerFactory.getLogger(RunAppService.class);
    private final SessionDomainService sessions;
    private final RunStore store;
    private final RunDispatch dispatch;
    private final AgentDomainService agents;
    private final AgentWorkspaceDomainService workspaces;
    private final LLMDomainService llms;
    private final RunKnowledgeVersionResolver knowledgeVersions;
    private final RunTargetRepositoryAuthorization targetRepositories;
    private final ApprovedActionReferenceValidator approvalReferences;
    private final RunTimelineRepository timeline;

    /** 保留轻量构造器，供只测试运行存储/派发边界的调用者使用。 */
    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch) {
        this(sessions, store, dispatch, null, null, null, RunKnowledgeVersionResolver.none(), null, null, null);
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms) {
        this(sessions, store, dispatch, agents, workspaces, llms, RunKnowledgeVersionResolver.none(), null, null, null);
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch,
            RunKnowledgeVersionResolver knowledgeVersions) {
        this(sessions, store, dispatch, null, null, null, knowledgeVersions, null, null, null);
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms,
            RunKnowledgeVersionResolver knowledgeVersions) {
        this(sessions, store, dispatch, agents, workspaces, llms, knowledgeVersions, null, null, null);
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms,
            RunKnowledgeVersionResolver knowledgeVersions, RunTargetRepositoryAuthorization targetRepositories) {
        this(sessions, store, dispatch, agents, workspaces, llms, knowledgeVersions, targetRepositories, null, null);
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms,
            RunKnowledgeVersionResolver knowledgeVersions, RunTargetRepositoryAuthorization targetRepositories,
            ApprovedActionReferenceValidator approvalReferences) {
        this(sessions, store, dispatch, agents, workspaces, llms, knowledgeVersions, targetRepositories,
                approvalReferences, null);
    }

    @Autowired
    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms,
            RunKnowledgeVersionResolver knowledgeVersions, RunTargetRepositoryAuthorization targetRepositories,
            ApprovedActionReferenceValidator approvalReferences, RunTimelineRepository timeline) {
        this.sessions = sessions;
        this.store = store;
        this.dispatch = dispatch;
        this.agents = agents;
        this.workspaces = workspaces;
        this.llms = llms;
        this.knowledgeVersions = knowledgeVersions;
        this.targetRepositories = targetRepositories;
        this.approvalReferences = approvalReferences;
        this.timeline = timeline;
    }

    /** 创建持久运行并派发工作；浏览器可以立即重新读取其状态。 */
    public RunDTO start(String sessionId, String goal, String userId) {
        return start(sessionId, goal, userId, RunApprovalPolicy.CREATOR_DEFAULT.name());
    }

    public RunDTO start(String sessionId, String goal, String userId, String requestedApprovalPolicy) {
        return start(sessionId, goal, userId, requestedApprovalPolicy, null, null);
    }

    /** Starts a run with explicit, server-validated knowledge and repository selections. */
    public RunDTO start(String sessionId, String goal, String userId, String requestedApprovalPolicy,
            String knowledgeBaseId, String requestedTargetRepository) {
        SessionEntity session = sessions.getSession(sessionId, userId);
        PinnedRunDefinition pinned = pinDefinition(session.getAgentId(), userId);
        String knowledgeVersionId = knowledgeVersions.resolve(session.getAgentId(), userId, knowledgeBaseId);
        String targetRepository = requestedTargetRepository == null || requestedTargetRepository.isBlank()
                ? null
                : requireTargetRepositoryAuthorizer().authorize(userId, requestedTargetRepository);
        RunEntity run = RunEntity.create(userId, sessionId, session.getAgentId(), goal, knowledgeVersionId);
        run.setTargetRepository(targetRepository);
        run.setApprovalPolicy(RunApprovalPolicy.fromValue(requestedApprovalPolicy).name());
        run.setAgentVersionId(pinned.agentVersionId());
        run.setAgentVersionNumber(pinned.agentVersionNumber());
        run.setModelId(pinned.modelId());
        run.setModelConnectionType(pinned.modelConnectionType());
        RunStepEntity step = RunStepEntity.modelCall(run.getId());
        store.createRunWithStep(run, step);
        appendTimeline(RunTimelineEvents.runCreated(run.getId(), step.getId()));
        try {
            dispatch.dispatch(run.getId());
        } catch (Exception exception) {
            // 扫描器会从 PostgreSQL 中再次找到 PENDING 的运行。
            logger.warn("运行派发暂不可用，等待数据库扫描恢复：{}", run.getId());
        }
        return RunAssembler.toDTO(run, List.of(step), null, timelineFor(run.getId()));
    }

    private RunTargetRepositoryAuthorization requireTargetRepositoryAuthorizer() {
        if (targetRepositories == null) {
            throw new BusinessException("目标仓库连接未授权或已撤销");
        }
        return targetRepositories;
    }

    /** 在写入运行权威记录前读取一次发布版本和模型连接，后续执行只依赖运行快照。 */
    private PinnedRunDefinition pinDefinition(String agentId, String userId) {
        // 旧的纯 Run 测试使用三参数构造器；生产服务始终使用带依赖的构造器。
        if (agents == null || workspaces == null || llms == null) {
            return PinnedRunDefinition.empty();
        }

        AgentVersionEntity version = agents.getPublishedAgentVersion(agentId);
        if (version == null || version.getId() == null) {
            throw new BusinessException("助理尚未发布可运行版本");
        }

        AgentWorkspaceEntity workspace = workspaces.getWorkspace(agentId, userId);
        String modelId = workspace.getLlmModelConfig().getModelId();
        if (modelId == null || modelId.isBlank()) {
            throw new BusinessException("助理尚未配置模型");
        }

        ModelEntity model = llms.getModelById(modelId);
        if (model == null || model.getProviderId() == null) {
            throw new BusinessException("模型连接不存在");
        }
        ProviderEntity provider = llms.getProvider(model.getProviderId());
        if (provider == null || provider.getProtocol() == null) {
            throw new BusinessException("模型连接类型不存在");
        }
        return new PinnedRunDefinition(version.getId(), version.getVersionNumber(), modelId,
                provider.getProtocol().name());
    }

    private record PinnedRunDefinition(String agentVersionId, String agentVersionNumber, String modelId,
            String modelConnectionType) {
        private static PinnedRunDefinition empty() {
            return new PinnedRunDefinition(null, null, null, null);
        }
    }

    /** 列出当前使用者在一个会话内的所有运行。 */
    public List<RunDTO> list(String sessionId, String userId) {
        sessions.getSession(sessionId, userId);
        return store.listOwnedBySession(sessionId, userId).stream().map(run -> RunAssembler.toDTO(run, List.of()))
                .toList();
    }

    /** 读取当前使用者可访问的运行及步骤。 */
    public RunDTO detail(String runId, String userId) {
        RunEntity run = store.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        return RunAssembler.toDTO(run, store.stepsForRun(runId), store.findApprovalForRun(runId), timelineFor(runId));
    }

    /** The run owner decides an immutable write request; approval re-queues its fixed action. */
    public RunDTO decideApproval(String runId, String approvalId, String userId, boolean approve) {
        RunEntity run = store.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        RunApprovalEntity approval = store.findApprovalForRun(runId);
        if (approval == null || !approvalId.equals(approval.getId())) {
            throw new EntityNotFoundException("审批请求不存在");
        }
        String decision = approve ? "APPROVED" : "DENIED";
        if (decision.equals(approval.getStatus())) {
            return detail(runId, userId);
        }
        if (!"PENDING".equals(approval.getStatus()) || !store.decideApproval(runId, approvalId, userId, approve)) {
            throw new BusinessException("审批请求已处理或运行已结束");
        }
        RunApprovalEntity decidedApproval = store.findApprovalForRun(runId);
        if (decidedApproval != null) {
            appendTimeline(RunTimelineEvents.approvalDecision(runId, firstStepId(runId), decidedApproval));
        }
        if (!approve) {
            appendTimeline(RunTimelineEvents.runFailed(runId, firstStepId(runId),
                    org.xhy.domain.run.model.RunFailureCode.APPROVAL_DENIED));
        }
        if (approve) {
            try {
                dispatch.dispatch(runId);
            } catch (Exception exception) {
                logger.warn("审批已记录，运行派发暂不可用，等待数据库扫描恢复：{}", runId);
            }
        }
        return detail(runId, userId);
    }

    /** Owner resolves an uncertain write with evidence, preserving the immutable approval record. */
    public RunDTO resolveReconciliation(String runId, String approvalId, String userId, String outcome, String evidence,
            String evidenceReference, String externalResourceId) {
        RunEntity run = store.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        RunApprovalEntity approval = store.findApprovalForRun(runId);
        if (approval == null || !approvalId.equals(approval.getId())) {
            throw new EntityNotFoundException("审批请求不存在");
        }
        if (!"WAITING_RECONCILIATION".equals(run.getStatus())
                || !"RECONCILIATION_REQUIRED".equals(approval.getStatus())) {
            throw new BusinessException("当前写入不在等待结果核对");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new BusinessException("必须提供结果核对依据");
        }
        if (evidence.trim().length() > 2000) {
            throw new BusinessException("结果核对依据不能超过 2000 个字符");
        }
        String normalizedEvidenceReference = evidenceReference == null || evidenceReference.isBlank()
                ? null
                : evidenceReference.trim();
        if (normalizedEvidenceReference != null && normalizedEvidenceReference.length() > 512) {
            throw new BusinessException("结果核对引用不能超过 512 个字符");
        }
        if ("CONFIRM_NOT_CREATED".equals(outcome) && normalizedEvidenceReference == null) {
            throw new BusinessException("确认未创建并重试时必须提供可核验的依据引用");
        }
        if (normalizedEvidenceReference != null && !requireApprovalReferenceValidator()
                .isValidEvidenceReference(normalizedEvidenceReference, approval)) {
            throw new BusinessException("依据引用必须是支持的记录 ID 或目标仓库中的规范 Issue URL");
        }
        if ("RECORD_CREATED".equals(outcome) && (externalResourceId == null || externalResourceId.isBlank())) {
            throw new BusinessException("记录已创建的结果时必须提供外部资源标识");
        }
        String normalizedExternalResourceId = externalResourceId == null ? null : externalResourceId.trim();
        if ("RECORD_CREATED".equals(outcome) && !requireApprovalReferenceValidator()
                .isValidExternalResourceId(normalizedExternalResourceId, approval.getTargetRepository())) {
            throw new BusinessException("外部资源标识必须是安全的资源 ID 或目标仓库中的 Issue URL");
        }
        if (!List.of("RECORD_CREATED", "CONFIRM_NOT_CREATED", "STOP").contains(outcome)) {
            throw new BusinessException("结果核对决定无效");
        }
        if (!store.resolveReconciliation(runId, approvalId, userId, outcome,
                RunTimelineSanitizer.redactText(evidence.trim()), normalizedEvidenceReference,
                normalizedExternalResourceId)) {
            throw new BusinessException("结果核对决定已处理或运行已变化");
        }
        appendTimeline(RunTimelineEvents.reconciliation(runId, firstStepId(runId), approval, outcome, userId));
        if (!"STOP".equals(outcome)) {
            try {
                dispatch.dispatch(runId);
            } catch (Exception exception) {
                logger.warn("结果核对已记录，运行派发暂不可用，等待数据库扫描恢复：{}", runId);
            }
        }
        return detail(runId, userId);
    }

    private ApprovedActionReferenceValidator requireApprovalReferenceValidator() {
        if (approvalReferences == null) {
            throw new BusinessException("结果核对引用验证暂不可用");
        }
        return approvalReferences;
    }

    /** 取消当前使用者拥有的未完成运行；SQL 层同时执行所有者校验与状态转换。 */
    public RunDTO cancel(String runId, String userId) {
        RunEntity run = store.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        if ("CANCELLED".equals(run.getStatus())) {
            return detail(runId, userId);
        }
        if (isFinished(run.getStatus())) {
            throw new BusinessException("运行已结束，无法取消");
        }
        if ("WAITING_RECONCILIATION".equals(run.getStatus())) {
            throw new BusinessException("外部写入结果不确定；请在结果核对中明确记录、确认未创建或停止");
        }
        if ("RUNNING".equals(run.getStatus()) && isApprovedActionExecuting(runId)) {
            throw new BusinessException("外部写入已开始，运行暂时无法取消");
        }
        if (!store.cancelOwned(runId, userId)) {
            RunEntity current = store.findOwned(runId, userId);
            if (current == null) {
                throw new EntityNotFoundException("运行不存在");
            }
            if ("CANCELLED".equals(current.getStatus())) {
                return detail(runId, userId);
            }
            if ("RUNNING".equals(current.getStatus()) && isApprovedActionExecuting(runId)) {
                throw new BusinessException("外部写入已开始，运行暂时无法取消");
            }
            throw new BusinessException("运行已结束，无法取消");
        }
        appendTimeline(RunTimelineEvents.runCancelled(runId, firstStepId(runId)));
        return detail(runId, userId);
    }

    private String firstStepId(String runId) {
        List<RunStepEntity> steps = store.stepsForRun(runId);
        return steps == null || steps.isEmpty() ? null : steps.get(0).getId();
    }

    private void appendTimeline(org.xhy.domain.run.model.RunTimelineEventEntity event) {
        if (timeline == null || event == null) {
            return;
        }
        try {
            timeline.append(event);
        } catch (RuntimeException exception) {
            logger.warn("运行时间线事件暂不可用：{}", event.getRunId());
        }
    }

    private List<org.xhy.domain.run.model.RunTimelineEventEntity> timelineFor(String runId) {
        if (timeline == null) {
            return List.of();
        }
        try {
            return timeline.eventsForRun(runId);
        } catch (RuntimeException exception) {
            logger.warn("运行时间线暂不可用：{}", runId);
            return List.of();
        }
    }

    private boolean isFinished(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private boolean isApprovedActionExecuting(String runId) {
        RunApprovalEntity approval = store.findApprovalForRun(runId);
        return approval != null && "EXECUTING".equals(approval.getStatus());
    }
}
