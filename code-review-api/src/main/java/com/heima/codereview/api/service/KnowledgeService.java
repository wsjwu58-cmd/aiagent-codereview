package com.heima.codereview.api.service;

import com.heima.codereview.common.model.knowledge.KnowledgeRecord;
import com.heima.codereview.common.model.knowledge.KnowledgeSearchResponse;
import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.knowledge.ReviewKnowledgeBase;
import com.heima.codereview.rag.retrieval.QueryRewriter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeService {

    private static final int DEFAULT_TOP_K = 8;

    private final ReviewKnowledgeBase reviewKnowledgeBase;
    private final QueryRewriter queryRewriter;

    public KnowledgeService(ReviewKnowledgeBase reviewKnowledgeBase, QueryRewriter queryRewriter) {
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.queryRewriter = queryRewriter;
    }

    public KnowledgeSearchResponse search(String query, String projectId, String sessionId, Integer topK) {
        int realTopK = topK == null ? DEFAULT_TOP_K : Math.max(1, Math.min(topK, 20));
        String normalizedQuery = query == null ? "" : query.trim();
        List<String> rewrittenQueries = queryRewriter.rewrite(normalizedQuery);
        List<ReviewRecord> records = reviewKnowledgeBase.search(normalizedQuery, projectId, sessionId, realTopK);
        return new KnowledgeSearchResponse(
                normalizedQuery,
                rewrittenQueries,
                records.stream()
                        .map(item -> new KnowledgeRecord(
                                item.id(),
                                item.reviewId(),
                                item.sessionId(),
                                item.projectId(),
                                item.content(),
                                item.timestamp()
                        ))
                        .toList()
        );
    }
}
