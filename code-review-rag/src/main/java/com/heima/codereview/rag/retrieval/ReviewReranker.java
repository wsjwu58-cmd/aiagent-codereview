package com.heima.codereview.rag.retrieval;

import com.heima.codereview.rag.model.ReviewRecord;

import java.util.List;

public interface ReviewReranker {

    List<ReviewRecord> rerank(String query, List<ReviewRecord> candidates, int topK);
}
