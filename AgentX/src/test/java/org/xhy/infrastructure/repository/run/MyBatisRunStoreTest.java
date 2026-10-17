package org.xhy.infrastructure.repository.run;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MyBatisRunStoreTest {
    @Test
    void shouldRequireReconciliationMapperSoAuditCannotBeSilentlySkipped() {
        assertThrows(NullPointerException.class, () -> new MyBatisRunStore(null, null, null, null));
    }

    @Test
    void shouldFailClosedWhenAppendOnlyAuditInsertDoesNotPersist() {
        RunMapper runs = proxy(RunMapper.class, (proxy, method, args) -> switch (method.getName()) {
            case "lockActiveLease" -> "run";
            case "waitForReconciliation" -> 1;
            default -> throw new AssertionError(method.getName());
        });
        RunStepMapper steps = proxy(RunStepMapper.class, (proxy, method, args) -> {
            assertEquals("waitFirstStepForReconciliation", method.getName());
            return 1;
        });
        RunApprovalMapper approvals = proxy(RunApprovalMapper.class, (proxy, method, args) -> {
            assertEquals("markReconciliationUnknown", method.getName());
            return 1;
        });
        RunReconciliationMapper reconciliations = proxy(RunReconciliationMapper.class, (proxy, method, args) -> {
            assertEquals("insert", method.getName());
            return 0;
        });
        MyBatisRunStore store = new MyBatisRunStore(runs, steps, approvals, reconciliations);

        assertThrows(IllegalStateException.class,
                () -> store.markReconciliationUnknown("run", "worker", "approval", "write result unknown"));
    }

    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
