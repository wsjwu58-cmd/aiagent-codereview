package com.heima.codereview.common.model.knowledge;

import java.util.List;

public record KnowledgeManagePageResponse(
        List<KnowledgeManageRecord> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
