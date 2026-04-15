package com.heima.codereview.common.model.knowledge;

public record KnowledgeRecord(
        String recordId,
        String reviewId,
        String sessionId,
        String projectId,
        String summary,
        long timestamp
) {
}
