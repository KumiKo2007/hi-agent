package org.xhy.interfaces.api.portal.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xhy.application.review.service.PrInputAppService;
import org.xhy.domain.review.model.PrInputSnapshot;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;

/** PR 只读输入公开 HTTP 入口。 */
@RestController
@RequestMapping("/reviews")
public class PrInputController {
    private final PrInputAppService inputs;

    public PrInputController(PrInputAppService inputs) {
        this.inputs = inputs;
    }

    /** 查看当前使用者可选择的个人连接和读取能力。 */
    @GetMapping("/input-options")
    public Result<PrInputAppService.InputOptions> options() {
        return Result.success(inputs.options(UserContext.getCurrentUserId()));
    }

    /** 完成授权校验、固定提交读取并保存快照。 */
    @PostMapping("/inputs")
    public Result<PrInputSnapshot> create(@RequestBody @Validated CreateInput request) {
        return Result.success(inputs.create(request.agentId(), request.serviceId(), request.pullRequestUrl(),
                UserContext.getCurrentUserId()));
    }

    /** 查看所有者的固定输入；不会重新访问移动 PR。 */
    @GetMapping("/inputs/{inputId}")
    public Result<PrInputSnapshot> get(@PathVariable String inputId) {
        return Result.success(inputs.get(inputId, UserContext.getCurrentUserId()));
    }

    public record CreateInput(@NotBlank @Size(max = 64) String agentId, @NotBlank @Size(max = 64) String serviceId,
            @NotBlank @Size(max = 500) String pullRequestUrl) {
    }
}
