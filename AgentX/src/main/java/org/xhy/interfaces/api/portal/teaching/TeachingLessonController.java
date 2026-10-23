package org.xhy.interfaces.api.portal.teaching;

import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xhy.application.teaching.request.CreateTeachingLessonRequest;
import org.xhy.application.teaching.service.TeachingLessonAppService;
import org.xhy.domain.generation.model.*;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;

/** 课时学生讲义与教师教案的公开HTTP边界。 */
@RestController
@RequestMapping("/teaching/lessons")
public class TeachingLessonController {
    private final TeachingLessonAppService service;

    public TeachingLessonController(TeachingLessonAppService service) {
        this.service = service;
    }

    /** 新建固定课时输入的双稿生成目标。 */
    @PostMapping
    public Result<GenerationArtifact> create(@RequestBody @Validated CreateTeachingLessonRequest request) {
        return Result.success(service.create(request, UserContext.getCurrentUserId()));
    }

    /** 执行生成并读取持久化的共同修订或失败原因。 */
    @PostMapping("/{id}/generate")
    public Result<GenerationArtifact> generate(@PathVariable String id) {
        return Result.success(service.generate(id, UserContext.getCurrentUserId()));
    }

    /** 精确读取历史修订，刷新不改变产物。 */
    @GetMapping("/{id}/revisions/{revisionId}")
    public Result<ArtifactRevision> revision(@PathVariable String id, @PathVariable String revisionId) {
        return Result.success(service.revision(id, revisionId, UserContext.getCurrentUserId()));
    }

    /** 读取已保存目标与运行。 */
    @GetMapping("/{id}")
    public Result<GenerationArtifact> get(@PathVariable String id) {
        return Result.success(service.get(id, UserContext.getCurrentUserId()));
    }

    /** 读取当前使用者的课时历史。 */
    @GetMapping
    public Result<List<GenerationArtifact>> list() {
        return Result.success(service.list(UserContext.getCurrentUserId()));
    }
}
