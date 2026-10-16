package org.xhy.interfaces.api.portal.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.application.run.service.KnowledgeRetrievalResult;
import org.xhy.application.run.service.KnowledgeSnapshotRetrievalService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.exception.EntityNotFoundException;
import org.xhy.interfaces.api.common.Result;

/** Public run-scoped knowledge retrieval. Every query uses the run's pinned version. */
@RestController
@RequestMapping("/agents/runs")
public class PortalAgentKnowledgeController {

    private final RunStore runs;
    private final KnowledgeSnapshotRetrievalService knowledge;

    public PortalAgentKnowledgeController(RunStore runs, KnowledgeSnapshotRetrievalService knowledge) {
        this.runs = runs;
        this.knowledge = knowledge;
    }

    /** Returns citations from the immutable snapshot pinned to this run. A no-evidence result is successful HTTP data
     * with {@code hasEvidence=false}; callers must not turn it into an automatic external write. */
    @GetMapping("/{runId}/knowledge")
    public Result<KnowledgeRetrievalResult> retrieve(@PathVariable String runId, @RequestParam String query) {
        String userId = UserContext.getCurrentUserId();
        RunEntity run = runs.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        return Result.success(knowledge.retrieve(run.getKnowledgeVersionId(), query, userId));
    }

    /** Reads one source referenced by a run result. */
    @GetMapping("/{runId}/knowledge/documents/{documentId}")
    public Result<?> citation(@PathVariable String runId, @PathVariable String documentId) {
        String userId = UserContext.getCurrentUserId();
        RunEntity run = runs.findOwned(runId, userId);
        if (run == null) {
            throw new EntityNotFoundException("运行不存在");
        }
        return Result.success(knowledge.findCitation(run.getKnowledgeVersionId(), documentId, userId));
    }
}
