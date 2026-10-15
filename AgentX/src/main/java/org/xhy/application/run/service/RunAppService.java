package org.xhy.application.run.service;

import org.springframework.stereotype.Service;
import org.xhy.application.run.assembler.RunAssembler;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.exception.EntityNotFoundException;

import java.util.List;

/** 每个目标独立创建和读取一次运行。 */
@Service
public class RunAppService {
    private final SessionDomainService sessions;
    private final RunStore store;
    private final RunModel model;

    public RunAppService(SessionDomainService sessions, RunStore store, RunModel model) {
        this.sessions = sessions;
        this.store = store;
        this.model = model;
    }

    /** 创建运行，保存模型步骤，并返回可重新读取的最终状态。 */
    public RunDTO start(String sessionId, String goal, String userId) {
        SessionEntity session = sessions.getSession(sessionId, userId);
        RunEntity run = RunEntity.create(userId, sessionId, session.getAgentId(), goal);
        RunStepEntity step = RunStepEntity.modelCall(run.getId());
        store.createRunWithStep(run, step);

        run.start();
        step.start();
        store.updateRunAndStep(run, step);
        try {
            String answer = model.execute(run);
            if (answer == null || answer.isBlank()) {
                throw new BusinessException("模型未返回结果");
            }
            run.complete(answer);
            step.complete(answer);
        } catch (Exception exception) {
            String reason = exception instanceof BusinessException ? exception.getMessage() : "模型执行失败";
            run.fail(reason);
            step.fail(reason);
        }
        store.updateRunAndStep(run, step);
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
