package com.heima.codereview.common.model.session;

public record SessionSummary(
        String sessionId,
        String projectId,
        String latestReviewId,
        String latestReviewStatus,
        String language,
        String latestMessagePreview,
        long lastActivity,
        int messageCount,
        int reviewCount
) {
}
