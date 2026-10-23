package org.xhy.interfaces.api.portal.teaching;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.xhy.application.teaching.service.TeachingOcrAppService;
import org.xhy.application.teaching.request.ReviewTeachingSourceRequest;
import org.xhy.domain.teaching.model.TeachingOcr;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;

/** 扫描PDF的逐页识别覆盖和人工核对入口。 */
@RestController
@RequestMapping("/teaching/ocr")
public class TeachingOcrController {
    private final TeachingOcrAppService service;
    public TeachingOcrController(TeachingOcrAppService service) {
        this.service = service;
    }
    @GetMapping("/versions/{versionId}/files/{fileId}")
    public Result<TeachingOcr.File> get(@PathVariable String versionId, @PathVariable String fileId) {
        return Result.success(service.get(versionId, fileId, UserContext.getCurrentUserId()));
    }
    @GetMapping("/files/{fileId}")
    public Result<TeachingOcr.File> current(@PathVariable String fileId) {
        return Result.success(service.current(fileId, UserContext.getCurrentUserId()));
    }
    @GetMapping("/versions/{versionId}/files/{fileId}/pages/{page}/image")
    public Result<String> image(@PathVariable String versionId, @PathVariable String fileId, @PathVariable int page) {
        return Result.success(service.image(versionId, fileId, page, UserContext.getCurrentUserId()));
    }
    @GetMapping("/files/{fileId}/pages/{page}/image")
    public Result<String> currentImage(@PathVariable String fileId, @PathVariable int page) {
        return Result.success(service.image(null, fileId, page, UserContext.getCurrentUserId()));
    }
    @PostMapping("/versions/{versionId}/files/{fileId}/pages/{page}/reviews")
    public Result<TeachingOcr.Page> review(@PathVariable String versionId, @PathVariable String fileId,
            @PathVariable int page, @RequestBody @Validated ReviewTeachingSourceRequest request) {
        return Result.success(service.review(versionId, fileId, page, request.note(), UserContext.getCurrentUserId()));
    }
}
