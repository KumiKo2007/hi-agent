package org.xhy.application.teaching.request;
import jakarta.validation.constraints.*;
/** 教师明确确认的模块范围与模型。 */
public record CreateTeachingOutlineRequest(@NotBlank(message = "请选择固定选材") String selectionId,
        @NotBlank(message = "请选择生成模型") String modelId,
        @NotBlank(message = "请完整填写教学参数") @Size(max = 100, message = "学科或年级最多100字") String subject,
        @NotBlank(message = "请完整填写教学参数") @Size(max = 100, message = "学科或年级最多100字") String level,
        @NotBlank(message = "请完整填写教学参数") @Size(max = 300, message = "教材名称最多300字") String textbook,
        @NotBlank(message = "请完整填写教学参数") @Size(max = 200, message = "教学参数最多200字") String edition,
        @NotBlank(message = "请完整填写教学参数") @Size(max = 200, message = "教学参数最多200字") String module,
        @Min(value = 10, message = "每课时至少10分钟") @Max(value = 180, message = "每课时最多180分钟") Integer lessonMinutes,
        @Size(max = 200, message = "教学参数最多200字") String studentFoundation,
        @NotNull(message = "请填写并确认模块总课时") @Min(value = 1, message = "模块至少1课时") @Max(value = 60, message = "模块最多60课时") Integer totalLessons,
        @AssertTrue(message = "请确认教材版本和总课时安排；版本待核对时请明确填写，不能默认为已匹配教材") boolean scopeConfirmed) {
}
