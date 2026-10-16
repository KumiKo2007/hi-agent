package org.xhy.interfaces.dto.tool.request;

import jakarta.validation.constraints.NotNull;
import org.xhy.domain.tool.constant.ToolActionClassification;

/** 平台管理员对 MCP 工具动作的审定。 */
public class ClassifyToolActionRequest {
    @NotNull
    private ToolActionClassification classification;

    public ToolActionClassification getClassification() {
        return classification;
    }

    public void setClassification(ToolActionClassification classification) {
        this.classification = classification;
    }

}
