package org.xhy.interfaces.api.portal.teaching;

import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xhy.application.teaching.request.*;
import org.xhy.application.teaching.service.TeachingOutlineAppService;
import org.xhy.domain.generation.model.*;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;

/** 模块大纲的公开HTTP行为边界。 */
@RestController
@RequestMapping("/teaching/outlines")
public class TeachingOutlineController {
    private final TeachingOutlineAppService service;
    public TeachingOutlineController(TeachingOutlineAppService service) {
        this.service = service;
    }
    @PostMapping
    public Result<GenerationArtifact> create(@RequestBody @Validated CreateTeachingOutlineRequest r) {
        return Result.success(service.create(r, UserContext.getCurrentUserId()));
    }
    @GetMapping
    public Result<List<GenerationArtifact>> list() {
        return Result.success(service.list(UserContext.getCurrentUserId()));
    }
    @GetMapping("/{id}")
    public Result<GenerationArtifact> get(@PathVariable String id) {
        return Result.success(service.get(id, UserContext.getCurrentUserId()));
    }
    @PostMapping("/{id}/generate")
    public Result<GenerationArtifact> generate(@PathVariable String id) {
        return Result.success(service.generate(id, UserContext.getCurrentUserId()));
    }
    @PutMapping("/{id}")
    public Result<GenerationArtifact> edit(@PathVariable String id,
            @RequestBody @Validated EditTeachingOutlineRequest r) {
        return Result.success(service.edit(id, r, UserContext.getCurrentUserId()));
    }
    @PostMapping("/{id}/confirm")
    public Result<GenerationArtifact> confirm(@PathVariable String id,
            @RequestBody @Validated ConfirmTeachingOutlineRequest r) {
        return Result.success(service.confirm(id, r.expectedRevisionId(), UserContext.getCurrentUserId()));
    }
    @GetMapping("/{id}/approved")
    public Result<GenerationArtifact> approved(@PathVariable String id) {
        return Result.success(service.approved(id, UserContext.getCurrentUserId()));
    }
    @GetMapping("/{id}/revisions/{revisionId}")
    public Result<ArtifactRevision> revision(@PathVariable String id, @PathVariable String revisionId) {
        return Result.success(service.revision(id, revisionId, UserContext.getCurrentUserId()));
    }
}
