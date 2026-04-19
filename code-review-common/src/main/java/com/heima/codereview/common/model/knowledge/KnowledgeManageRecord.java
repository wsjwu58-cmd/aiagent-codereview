package com.heima.codereview.common.model.knowledge;

import java.util.Map;

public record KnowledgeManageRecord(
        String id,
        KnowledgeRecordType type,
        String projectId,
        String sessionId,
        String reviewId,
        String fileName,
        String summary,
        long createdAt,
        Map<String, Object> metadata
) {
}
