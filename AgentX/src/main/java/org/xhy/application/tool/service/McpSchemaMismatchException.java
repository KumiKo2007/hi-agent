package org.xhy.application.tool.service;

import org.xhy.infrastructure.exception.BusinessException;

/** Signals that execution must pause because a frozen MCP action schema no longer matches. */
public final class McpSchemaMismatchException extends BusinessException {
    public static final String USER_MESSAGE = "MCP 工具参数结构与运行时结构已变化，运行已暂停；请检查动作定义后重新运行。";

    public McpSchemaMismatchException() {
        super(USER_MESSAGE);
    }
}
