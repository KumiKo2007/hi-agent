package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A run captures the ready knowledge version before it is dispatched. */
class RunAppServiceKnowledgePinTest {

    @Test
    void pinsKnowledgeVersionBeforeDispatchingTheRun() {
        SessionDomainService sessions = mock(SessionDomainService.class);
        RunStore store = mock(RunStore.class);
        RunDispatch dispatch = mock(RunDispatch.class);
        RunKnowledgeVersionResolver resolver = mock(RunKnowledgeVersionResolver.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);
        when(resolver.resolve("agent-1", "owner")).thenReturn("version-1");
        doNothing().when(store).createRunWithStep(any(RunEntity.class), any(RunStepEntity.class));

        RunAppService service = new RunAppService(sessions, store, dispatch, resolver);

        assertEquals("version-1", service.start("session-1", "整理网关资料", "owner").getKnowledgeVersionId());
        ArgumentCaptor<RunEntity> run = ArgumentCaptor.forClass(RunEntity.class);
        verify(store).createRunWithStep(run.capture(), any(RunStepEntity.class));
        assertEquals("version-1", run.getValue().getKnowledgeVersionId());
        verify(dispatch).dispatch(run.getValue().getId());
    }
}
