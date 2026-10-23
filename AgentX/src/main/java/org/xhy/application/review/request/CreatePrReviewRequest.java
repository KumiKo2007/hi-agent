package org.xhy.application.review.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 审查目标只引用已固定的输入，不接受新的工具权限。 */
public record CreatePrReviewRequest(@NotBlank @Size(max = 64) String inputId,
        @NotBlank @Size(max = 64) String modelId) {
}
