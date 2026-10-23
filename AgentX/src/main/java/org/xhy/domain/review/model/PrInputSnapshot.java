package org.xhy.domain.review.model;

import java.util.List;

/** 固定提交的 PR 输入。来源文本均是不可信资料，不能改变工具权限。 */
public record PrInputSnapshot(String id, String agentId, String serviceId, String repository, int pullNumber,
        String pullRequestUrl, String baseSha, String headSha, String title, String body, List<ChangedFile> files,
        List<SourceContext> contexts, List<String> gaps, String createdAt) {
    public PrInputSnapshot {
        files = List.copyOf(files);
        contexts = List.copyOf(contexts);
        gaps = List.copyOf(gaps);
    }

    public record ChangedFile(String path, String previousPath, String status, String patch) {
    }

    public record SourceContext(String repository, String path, String commitSha, String content) {
    }
}
