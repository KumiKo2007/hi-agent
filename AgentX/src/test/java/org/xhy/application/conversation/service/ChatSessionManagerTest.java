package org.xhy.application.conversation.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 对话中断必须绑定到创建 SSE 会话的用户。 */
class ChatSessionManagerTest {

    @Test
    void should_keepActiveSessionWhenAnotherUserTriesToInterruptIt() {
        ChatSessionManager manager = new ChatSessionManager();
        manager.registerSession("session-1", "owner", new SseEmitter());

        assertFalse(manager.interruptSession("session-1", "outsider"));
        assertTrue(manager.hasSession("session-1"));
        assertEquals(1, manager.getActiveSessionCount());

        assertTrue(manager.interruptSession("session-1", "owner"));
        assertFalse(manager.hasSession("session-1"));
    }
}
