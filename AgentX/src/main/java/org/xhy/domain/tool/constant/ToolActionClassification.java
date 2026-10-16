package org.xhy.domain.tool.constant;

/** 平台对单个 MCP 工具动作的安全分类；未登记时按写入处理。 */
public enum ToolActionClassification {
    READ_ONLY, WRITE_REQUIRES_APPROVAL
}
