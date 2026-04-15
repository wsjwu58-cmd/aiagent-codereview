package com.heima.codereview.common.model.review;

public record IncrementalReviewRequest(
        String projectId,
        String repoUrl,
        String branch,
        String baseCommit,
        String headCommit,
        String sessionId,
        String language
) {
}
