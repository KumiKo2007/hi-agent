package org.xhy.interfaces.api.portal.teaching;

import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xhy.application.teaching.service.TeachingSelectionAppService;
import org.xhy.domain.teaching.model.TeachingSelection;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;
import org.xhy.application.teaching.request.SaveTeachingSelectionRequest;

/** 教学选材的公开入口。 */
@RestController
@RequestMapping("/teaching/selections")
public class TeachingSelectionController {
    private final TeachingSelectionAppService service;
    public TeachingSelectionController(TeachingSelectionAppService service) {
        this.service = service;
    }
    @PostMapping
    public Result<TeachingSelection> save(@RequestBody @Validated SaveTeachingSelectionRequest request) {
        return Result.success(service.save(request, UserContext.getCurrentUserId()));
    }
    @GetMapping
    public Result<List<TeachingSelection>> list() {
        return Result.success(service.list(UserContext.getCurrentUserId()));
    }
    @GetMapping("/{id}")
    public Result<TeachingSelection> get(@PathVariable String id) {
        return Result.success(service.get(id, UserContext.getCurrentUserId()));
    }
    @GetMapping("/{id}/sources/{sourceId}")
    public Result<TeachingSelection.Source> source(@PathVariable String id, @PathVariable String sourceId) {
        return Result.success(service.source(id, sourceId, UserContext.getCurrentUserId()));
    }
}
