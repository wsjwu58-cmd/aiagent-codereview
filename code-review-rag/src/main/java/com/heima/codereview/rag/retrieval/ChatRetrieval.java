package com.heima.codereview.rag.retrieval;

import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.vector.MilvusRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatRetrieval {

    private static final int DEFAULT_TOP_K = 5;

    private final MilvusRepository milvusRepository;
    private final HybridSearch hybridSearch;
    private final QueryRewriter queryRewriter;
    private final ReviewReranker reviewReranker;

    public ChatRetrieval(MilvusRepository milvusRepository,
                         HybridSearch hybridSearch,
                         QueryRewriter queryRewriter,
                         ReviewReranker reviewReranker) {
        this.milvusRepository = milvusRepository;
        this.hybridSearch = hybridSearch;
        this.queryRewriter = queryRewriter;
        this.reviewReranker = reviewReranker;
    }

    public List<ReviewRecord> retrieve(String query) {
        return retrieve(query, null, null, DEFAULT_TOP_K);
    }

    public List<ReviewRecord> retrieve(String query, String projectId, String sessionId) {
        return retrieve(query, projectId, sessionId, DEFAULT_TOP_K);
    }

    public List<ReviewRecord> retrieve(String query, String projectId, String sessionId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> expandedQueries = queryRewriter.rewrite(query);
        Map<String, ReviewRecord> merged = new LinkedHashMap<>();
        int candidateLimit = Math.max(topK * 3, topK);

        for (String expandedQuery : expandedQueries) {
            List<ReviewRecord> results = hybridSearch.search(expandedQuery, projectId, sessionId, candidateLimit);
            results.forEach(record -> merged.putIfAbsent(record.id(), record));
        }

        return reviewReranker.rerank(query, new ArrayList<>(merged.values()), Math.max(1, topK));
    }

    public List<ReviewRecord> latest(String projectId, String sessionId, int limit) {
        return milvusRepository.search("", projectId, sessionId, limit);
    }
}
