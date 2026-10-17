package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.repository.SessionRepository;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A run captures its knowledge snapshot before it is dispatched. */
class RunAppServiceKnowledgePinTest {

    @Test
    void pinsKnowledgeVersionBeforeDispatchingTheRun() {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        SessionRepository sessionRepository = proxy(SessionRepository.class, (method,
                args) -> "selectOne".equals(method.getName()) ? session : defaultValue(method.getReturnType()));
        AtomicReference<RunEntity> createdRun = new AtomicReference<>();
        RunStore store = proxy(RunStore.class, (method, args) -> {
            if ("createRunWithStep".equals(method.getName()))
                createdRun.set((RunEntity) args[0]);
            return defaultValue(method.getReturnType());
        });
        AtomicReference<String> dispatchedId = new AtomicReference<>();
        RunKnowledgeVersionResolver resolver = (agentId, userId) -> {
            assertEquals("agent-1", agentId);
            assertEquals("owner", userId);
            return "version-1";
        };

        RunAppService service = new RunAppService(new SessionDomainService(sessionRepository), store, dispatchedId::set,
                resolver);

        assertEquals("version-1", service.start("session-1", "整理网关资料", "owner").getKnowledgeVersionId());
        assertEquals("version-1", createdRun.get().getKnowledgeVersionId());
        assertEquals(createdRun.get().getId(), dispatchedId.get());
    }

    private static <T> T proxy(Class<T> type, BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> handler.apply(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }
}
