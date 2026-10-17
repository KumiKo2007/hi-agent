package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.repository.SessionRepository;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunApprovalPolicy;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.exception.BusinessException;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunAppServiceApprovalPolicyTest {
    @Test
    void shouldPersistTheUsersStricterPolicyOnTheRun() {
        AtomicReference<RunEntity> saved = new AtomicReference<>();
        RunStore store = proxy(RunStore.class, (method, args) -> {
            if ("createRunWithStep".equals(method.getName())) {
                saved.set((RunEntity) args[0]);
            }
            return null;
        });
        RunAppService service = new RunAppService(sessions(), store, ignored -> { });

        RunDTO result = service.start("session-1", "Create an issue", "owner",
                RunApprovalPolicy.REQUIRE_APPROVAL_FOR_ALL_WRITES.name());

        assertEquals(RunApprovalPolicy.REQUIRE_APPROVAL_FOR_ALL_WRITES.name(), result.getApprovalPolicy());
        assertEquals(RunApprovalPolicy.REQUIRE_APPROVAL_FOR_ALL_WRITES.name(), saved.get().getApprovalPolicy());
    }

    @Test
    void shouldRejectUnknownPoliciesInsteadOfAllowingThemToWeakenApproval() {
        RunStore store = proxy(RunStore.class, (method, args) -> null);
        RunAppService service = new RunAppService(sessions(), store, ignored -> { });

        assertThrows(BusinessException.class, () -> service.start("session-1", "Create an issue", "owner", "NONE"));
    }

    private static SessionDomainService sessions() {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        SessionRepository repository = proxy(SessionRepository.class, (method, args) -> null);
        return new SessionDomainService(repository) {
            @Override
            public SessionEntity getSession(String sessionId, String userId) {
                return session;
            }
        };
    }

    private static <T> T proxy(Class<T> type, BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (ignored, method, args) -> handler.apply(method, args)));
    }
}
