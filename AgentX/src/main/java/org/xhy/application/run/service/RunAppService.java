package org.xhy.application.run.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.application.run.assembler.RunAssembler;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.exception.EntityNotFoundException;

import java.util.List;

/** 每个目标独立创建和读取一次运行。 */
@Service
public class RunAppService {
    private static final Logger logger = LoggerFactory.getLogger(RunAppService.class);
    private final SessionDomainService sessions;
    private final RunStore store;
    private final RunDispatch dispatch;

    public RunAppService(SessionDomainService sessions, RunStore store, RunDispatch dispatch) {
        this.sessions = sessions;
        this.store = store;
        this.dispatch = dispatch;
    }

    /** 创建持久运行并派发工作；浏览器可以立即重新读取其状态。 */
    public RunDTO start(String sessionId, String goal, String userId) {
        SessionEntity session = sessions.getSession(sessionId, userId);
        RunEntity run = RunEntity.create(userId, sessionId, session.getAgentId(), goal);
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
}
