package org.xhy.interfaces.api.portal.review;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xhy.application.review.request.CreatePrReviewRequest;
import org.xhy.application.review.service.PrReviewAppService;
import org.xhy.domain.generation.model.*;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;

/** 固定输入的Bug审查、状态、历史与指定修订下载。 */
@RestController
@RequestMapping("/reviews/reports")
public class PrReviewController {
    private final PrReviewAppService service;
    public PrReviewController(PrReviewAppService service) {
        this.service = service;
    }
    /** 保存独立审查目标。 */
    @PostMapping
    public Result<GenerationArtifact> create(@RequestBody @Validated CreatePrReviewRequest request) {
        return Result.success(service.create(request, UserContext.getCurrentUserId()));
    }
    /** 查看所有者的目标列表。 */
    @GetMapping
    public Result<List<GenerationArtifact>> list() {
        return Result.success(service.list(UserContext.getCurrentUserId()));
    }
    /** 查看保存的状态及当前报告。 */
    @GetMapping("/{id}")
    public Result<GenerationArtifact> get(@PathVariable String id) {
        return Result.success(service.get(id, UserContext.getCurrentUserId()));
    }
    /** 无工具模型生成。 */
    @PostMapping("/{id}/generate")
    public Result<GenerationArtifact> generate(@PathVariable String id) {
        return Result.success(service.generate(id, UserContext.getCurrentUserId()));
    }
    /** 查看精确修订。 */
    @GetMapping("/{id}/revisions/{revisionId}")
    public Result<ArtifactRevision> revision(@PathVariable String id, @PathVariable String revisionId) {
        return Result.success(service.revision(id, revisionId, UserContext.getCurrentUserId()));
    }
    /** 下载精确修订的Markdown；文件名不使用来源文字。 */
    @GetMapping("/{id}/revisions/{revisionId}/markdown")
    public ResponseEntity<byte[]> markdown(@PathVariable String id, @PathVariable String revisionId) {
        byte[] body = service.markdown(id, revisionId, UserContext.getCurrentUserId()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pr-review.md")
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(body);
    }
}
