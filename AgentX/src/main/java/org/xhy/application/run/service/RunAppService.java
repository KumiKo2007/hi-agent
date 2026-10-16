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
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;
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

    /** 保留轻量构造器，供只测试运行存储/派发边界的调用者使用。 */
    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch) {
        this(sessions, store, dispatch, null, null, null, RunKnowledgeVersionResolver.none());
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms) {
        this(sessions, store, dispatch, agents, workspaces, llms, RunKnowledgeVersionResolver.none());
    }

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch,
            RunKnowledgeVersionResolver knowledgeVersions) {
        this(sessions, store, dispatch, null, null, null, knowledgeVersions);
    }

    @Autowired
    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch, AgentDomainService agents,
            AgentWorkspaceDomainService workspaces, LLMDomainService llms,
            RunKnowledgeVersionResolver knowledgeVersions) {
        this.sessions = sessions;
        this.store = store;
        this.dispatch = dispatch;
        this.agents = agents;
        this.workspaces = workspaces;
        this.llms = llms;
        this.knowledgeVersions = knowledgeVersions;
    }

    /** 创建持久运行并派发工作；浏览器可以立即重新读取其状态。 */
    public RunDTO start(String sessionId, String goal, String userId) {
        SessionEntity session = sessions.getSession(sessionId, userId);
        PinnedRunDefinition pinned = pinDefinition(session.getAgentId(), userId);
        String knowledgeVersionId = knowledgeVersions.resolve(session.getAgentId(), userId);
        RunEntity run = RunEntity.create(userId, sessionId, session.getAgentId(), goal, knowledgeVersionId);
        run.setAgentVersionId(pinned.agentVersionId());
        run.setAgentVersionNumber(pinned.agentVersionNumber());
        run.setModelId(pinned.modelId());
        run.setModelConnectionType(pinned.modelConnectionType());
        RunStepEntity step = RunStepEntity.modelCall(run.getId());
        store.createRunWithStep(run, step);
        try {
            dispatch.dispatch(run.getId());
        } catch (Exception exception) {
            // 扫描器会从 PostgreSQL 中再次找到 PENDING 的运行。
            logger.warn("运行派发暂不可用，等待数据库扫描恢复：{}", run.getId());
        }
        return RunAssembler.toDTO(run, List.of(step));
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
        return RunAssembler.toDTO(run, store.stepsForRun(runId));
    }

    /** 取消当前使用者拥有的未完成运行；SQL 层同时执行所有者校验与状态转换。 */
    public RunDTO cancel(String runId, String userId) {
        RunEntity run = store.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        if ("CANCELLED".equals(run.getStatus())) {
            return RunAssembler.toDTO(run, store.stepsForRun(runId));
        }
        if (isFinished(run.getStatus())) {
            throw new BusinessException("运行已结束，无法取消");
        }
        if (!store.cancelOwned(runId, userId)) {
            RunEntity current = store.findOwned(runId, userId);
            if (current == null) {
                throw new EntityNotFoundException("运行不存在");
            }
            if ("CANCELLED".equals(current.getStatus())) {
                return RunAssembler.toDTO(current, store.stepsForRun(runId));
            }
            throw new BusinessException("运行已结束，无法取消");
        }
        return detail(runId, userId);
    }

    private boolean isFinished(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }
}
