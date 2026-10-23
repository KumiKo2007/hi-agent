package org.xhy.application.teaching.request;
import jakarta.validation.constraints.NotBlank;
/** 确认精确的一份可见修订。 */
public record ConfirmTeachingOutlineRequest(@NotBlank(message = "请刷新后读取要确认的大纲修订") String expectedRevisionId) {
}
