package org.xhy.application.teaching.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 不信任客户端提交的来源内容；仅接收版本和明确页码范围。 */
public record SaveTeachingSelectionRequest(
        @NotBlank(message = "选材名称不能为空") @Size(max = 120, message = "选材名称最多 120 字") String title,
        @NotBlank(message = "请选择知识库") String datasetId, @NotBlank(message = "请确认知识版本") String versionId,
        @NotEmpty(message = "请至少选择一个文件范围") @Size(max = 20, message = "一次最多选择 20 个文件范围") List<@NotNull(message = "文件范围不能为空") @Valid Range> ranges) {
    public record Range(@NotBlank(message = "请选择文件") String fileId,
            @Min(value = 1, message = "文件物理页从 1 开始") int startPage,
            @Min(value = 1, message = "文件物理页从 1 开始") int endPage,
            @Size(max = 200, message = "章节标记最多 200 字") String chapter) {
    }
}
