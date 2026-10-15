package org.xhy.interfaces.dto.run.request;

import jakarta.validation.constraints.NotBlank;

/** 提交给 Agent 的一个目标。 */
public class StartRunRequest {
    @NotBlank(message = "目标不可为空")
    private String goal;

    public String getGoal() {
        return goal;
    }
    public void setGoal(String goal) {
        this.goal = goal;
    }
}
