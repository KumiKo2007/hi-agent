package org.xhy.application.teaching.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 人工说明核对依据；不接受系统权限或内容替换指令。 */
public record ReviewTeachingSourceRequest(@NotBlank(message = "请记录原页核对依据") @Size(max = 2000) String note) {
}
