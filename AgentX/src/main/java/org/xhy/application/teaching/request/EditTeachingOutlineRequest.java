package org.xhy.application.teaching.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
/** 带当前修订令牌的大纲修改。 */
public record EditTeachingOutlineRequest(@NotBlank(message = "请刷新后读取当前大纲修订") String expectedRevisionId,
        @NotEmpty(message = "请至少保留一个课时") @Size(max = 60, message = "大纲最多60课时") List<@Valid Lesson> lessons) {
    public record Lesson(@NotBlank(message = "请填写课时主题") @Size(max = 300, message = "课时主题最多300字") String title,
            @NotEmpty(message = "请填写学习目标") @Size(max = 20, message = "每课最多20个目标") List<@NotBlank(message = "学习目标不能为空") @Size(max = 1000, message = "每个学习目标最多1000字") String> objectives) {
    }
}
