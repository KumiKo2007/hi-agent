package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.repository.SessionRepository;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.exception.BusinessException;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunAppServiceTargetRepositoryTest {

    @Test
    void validatesAndPinsTheSelectedRepositoryBeforeDispatch() {
        SessionEntity session = session();
        AtomicReference<RunEntity> createdRun = new AtomicReference<>();
        AtomicReference<String> authorizedRepository = new AtomicReference<>();
        AtomicReference<String> dispatched = new AtomicReference<>();
        RunStore store = store(createdRun);
        RunTargetRepositoryAuthorization authorizer = authorizer((user, repository) -> {
            if (!"owner".equals(user) || !"kumiko2007/hi-agent".equals(repository)) {
                throw new BusinessException("not authorized");
            }
            authorizedRepository.set(repository);
        });
        RunAppService service = new RunAppService(sessions(session), store, dispatched::set, null, null, null,
                RunKnowledgeVersionResolver.none(), authorizer);

        var result = service.start("session-1", "Create an issue", "owner", "CREATOR_DEFAULT", null,
                "KumiKo2007/Hi-Agent");

        assertEquals("kumiko2007/hi-agent", result.getTargetRepository());
        assertEquals("kumiko2007/hi-agent", createdRun.get().getTargetRepository());
        assertEquals("kumiko2007/hi-agent", authorizedRepository.get());
        assertEquals(createdRun.get().getId(), dispatched.get());
    }

    @Test
    void rejectsAnUnapprovedRepositoryBeforeCreatingARun() {
        AtomicReference<RunEntity> createdRun = new AtomicReference<>();
        RunTargetRepositoryAuthorization authorizer = authorizer((user, repository) -> {
            throw new BusinessException("not authorized");
        });
        RunAppService service = new RunAppService(sessions(session()), store(createdRun), ignored -> {
        }, null, null, null, RunKnowledgeVersionResolver.none(), authorizer);

        assertThrows(BusinessException.class, () -> service.start("session-1", "Create an issue", "owner",
                "CREATOR_DEFAULT", null, "outside/private"));
        assertEquals(null, createdRun.get());
    }

    private static SessionEntity session() {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        return session;
    }

    private static SessionDomainService sessions(SessionEntity session) {
        SessionRepository repository = proxy(SessionRepository.class, (method, args) ->
                "selectOne".equals(method.getName()) ? session : defaultValue(method.getReturnType()));
        return new SessionDomainService(repository);
    }

    private static RunStore store(AtomicReference<RunEntity> createdRun) {
        return proxy(RunStore.class, (method, args) -> {
            if ("createRunWithStep".equals(method.getName())) {
                createdRun.set((RunEntity) args[0]);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static RunTargetRepositoryAuthorization authorizer(AuthorizationCheck check) {
        return new RunTargetRepositoryAuthorization() {
            @Override
            public String authorize(String user, String repository) {
                String normalized = repository.trim().toLowerCase(java.util.Locale.ROOT);
                check.verify(user, normalized);
                return normalized;
            }

            @Override
            public void revalidate(String user, String repository) {
                check.verify(user, repository);
            }
        };
    }

    private static <T> T proxy(Class<T> type, BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (ignored, method, args) -> handler.apply(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    @FunctionalInterface
    private interface AuthorizationCheck {
        void verify(String owner, String repository);
    }
}
