package com.heima.codereview.rag.knowledge;

import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.retrieval.ChatRetrieval;
import com.heima.codereview.rag.vector.MilvusRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReviewKnowledgeBase {

    private static final String COLLECTION_NAME = "code_review_knowledge";

    private final MilvusRepository milvusRepository;
    private final ChatRetrieval chatRetrieval;

    public ReviewKnowledgeBase(MilvusRepository milvusRepository,
                               ChatRetrieval chatRetrieval) {
        this.milvusRepository = milvusRepository;
        this.chatRetrieval = chatRetrieval;
    }

    public void saveRecord(ReviewRecord record) {
        milvusRepository.insert(COLLECTION_NAME, record);
    }

    public void saveRecord(ReviewRecord record, Map<String, Object> metadata) {
        milvusRepository.insert(COLLECTION_NAME, record, metadata);
    }

    public List<ReviewRecord> search(String query, String projectId, String sessionId, int topK) {
        return chatRetrieval.retrieve(query, projectId, sessionId, topK);
    }

    public Optional<Map<String, Object>> findMetadataByReviewId(String reviewId) {
        return milvusRepository.findMetadataByReviewId(reviewId);
    }
}
