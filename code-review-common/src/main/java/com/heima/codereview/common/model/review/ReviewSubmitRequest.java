package com.heima.codereview.common.model.review;

public record ReviewSubmitRequest(
        ReviewType type,
        String repoUrl,
        String branch,
        String codeContent,
        String projectId,
        String sessionId,
        String language,
        String templateId
) {
}
