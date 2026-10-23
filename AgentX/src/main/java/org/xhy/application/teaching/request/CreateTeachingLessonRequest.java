package org.xhy.application.teaching.request;

import jakarta.validation.constraints.*;

/** 逐课双稿绑定教师当前确认的精确大纲修订。 */
public record CreateTeachingLessonRequest(@NotBlank(message = "请选择已确认大纲") String outlineId,
        @NotBlank(message = "请提供已确认大纲修订") String expectedOutlineRevisionId,
        @NotNull(message = "请选择课时") @Min(value = 1, message = "课时从1开始") Integer lessonNumber,
        @NotBlank(message = "请选择已配置生成模型") String modelId) {
}
