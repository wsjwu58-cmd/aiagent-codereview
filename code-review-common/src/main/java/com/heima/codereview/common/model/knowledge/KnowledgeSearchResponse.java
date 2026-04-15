package com.heima.codereview.common.model.knowledge;

import java.util.List;

public record KnowledgeSearchResponse(
        String query,
        List<String> rewrittenQueries,
        List<KnowledgeRecord> records
) {
}
