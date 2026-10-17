package org.xhy.domain.run.model;

import org.xhy.infrastructure.exception.BusinessException;

/** Finite safe failure codes used in the owner timeline and next-action guidance. */
public enum RunFailureCode {
    INSUFFICIENT_EVIDENCE("资料依据不足。补充或选择资料后重新运行。"),
    TARGET_REPOSITORY_NOT_AUTHORIZED("目标仓库未授权。检查仓库连接后重新运行。"),
    PERMISSION_DENIED("工具权限不足。检查连接权限后重新运行。"),
    MODEL_UNAVAILABLE("模型连接不可用。检查模型设置后重新运行。"),
    TOOL_SCHEMA_CHANGED("工具结构已变化。检查工具配置后重新运行。"),
    APPROVAL_DENIED("所有者拒绝了外部写入。需要新目标时请重新运行。"),
    EXECUTION_FAILED("运行执行失败。检查运行步骤后重新运行。");

    private final String safeMessage;

    RunFailureCode(String safeMessage) {
        this.safeMessage = safeMessage;
    }

    public String safeMessage() {
        return safeMessage;
    }

    public static RunFailureCode classify(Throwable failure) {
        if (!(failure instanceof BusinessException) || failure.getMessage() == null) {
            return EXECUTION_FAILED;
        }
        String message = failure.getMessage();
        if (message.contains("资料依据不足") || message.contains("固定资料未支持")) {
            return INSUFFICIENT_EVIDENCE;
        }
        if (message.contains("仓库") && (message.contains("授权") || message.contains("权限"))) {
            return TARGET_REPOSITORY_NOT_AUTHORIZED;
        }
        if (message.contains("权限") || message.contains("拒绝访问")) {
            return PERMISSION_DENIED;
        }
        if (message.contains("模型") && (message.contains("配置") || message.contains("连接")
                || message.contains("不存在") || message.contains("禁用"))) {
            return MODEL_UNAVAILABLE;
        }
        return EXECUTION_FAILED;
    }
}
