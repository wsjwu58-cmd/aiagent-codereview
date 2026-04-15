package com.heima.codereview.rag.vector;

import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.rag.model.ReviewRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MilvusRepository {

    private static final Logger log = LoggerFactory.getLogger(MilvusRepository.class);

    private final MilvusVectorStore vectorStore;
    private final String collectionName;
    private final int defaultTopK = 5;
    private final Map<String, CachedReviewEntry> localCache = new ConcurrentHashMap<>();

    public MilvusRepository(MilvusVectorStore vectorStore,
                            @Value("${spring.ai.vectorstore.milvus.collection-name:code_review_knowledge}") String collectionName) {
        this.vectorStore = vectorStore;
        this.collectionName = collectionName;
        log.info("MilvusRepository initialized. collection={}", collectionName);
    }

    public void insert(String collectionName, ReviewRecord record) {
        insert(collectionName, record, Map.of());
    }

    public void insert(String collectionName, ReviewRecord record, Map<String, Object> metadata) {
        try {
            Map<String, Object> docMetadata = new LinkedHashMap<>();
            docMetadata.put("reviewId", safe(record.reviewId()));
            docMetadata.put("sessionId", safe(record.sessionId()));
            docMetadata.put("projectId", safe(record.projectId()));
            docMetadata.put("recordId", safe(record.id()));
            docMetadata.put("timestamp", record.timestamp());
            if (metadata != null) {
                metadata.forEach((key, value) -> {
                    if (value != null) {
                        docMetadata.put(key, value);
                    }
                });
            }

            String documentId = IdUtils.compactWithPrefix("record", 32);
            Document doc = new Document(documentId, safe(record.content()), docMetadata);
            vectorStore.add(List.of(doc));
            cacheRecord(toReviewRecord(doc, record.projectId(), record.sessionId()), docMetadata);
            log.debug("Inserted review document. collection={}, id={}", this.collectionName, record.id());
        } catch (Exception e) {
            log.error("Failed to insert review document. collection={}, id={}", this.collectionName, record.id(), e);
        }
    }

    public List<ReviewRecord> search(String query, String collectionName, int topK) {
        return search(query, null, null, topK);
    }

    public List<ReviewRecord> search(String query, String projectId, String sessionId, int topK) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query == null ? "" : query)
                    .topK(topK > 0 ? topK : defaultTopK)
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);
            List<ReviewRecord> results = new ArrayList<>();
            for (Document doc : docs) {
                ReviewRecord record = toReviewRecord(doc, projectId, sessionId);
                if (!matchesProject(record.projectId(), projectId)) {
                    continue;
                }
                if (!matchesSession(record.sessionId(), sessionId)) {
                    continue;
                }
                results.add(record);
                cacheRecord(record, doc.getMetadata());
            }
            return results;
        } catch (Exception e) {
            log.error("Vector search failed. collection={}, query={}", this.collectionName, query, e);
            return List.of();
        }
    }

    public List<ReviewRecord> cachedRecords(String projectId, String sessionId) {
        return localCache.values().stream()
                .map(CachedReviewEntry::record)
                .filter(record -> matchesProject(record.projectId(), projectId))
                .filter(record -> matchesSession(record.sessionId(), sessionId))
                .sorted((left, right) -> Long.compare(right.timestamp(), left.timestamp()))
                .toList();
    }

    public Optional<Map<String, Object>> findMetadataByReviewId(String reviewId) {
        if (reviewId == null || reviewId.isBlank()) {
            return Optional.empty();
        }
        return localCache.values().stream()
                .filter(entry -> Objects.equals(reviewId, entry.record().reviewId()))
                .map(CachedReviewEntry::metadata)
                .findFirst();
    }

    private ReviewRecord toReviewRecord(Document doc, String fallbackProjectId, String fallbackSessionId) {
        Map<String, Object> metadata = doc.getMetadata();
        String recordId = metadata.get("recordId") instanceof String recordIdValue && !recordIdValue.isBlank()
                ? recordIdValue
                : doc.getId();
        String projectId = metadata.get("projectId") instanceof String projectIdValue && !projectIdValue.isBlank()
                ? projectIdValue
                : safe(fallbackProjectId);
        String sessionId = metadata.get("sessionId") instanceof String sessionIdValue && !sessionIdValue.isBlank()
                ? sessionIdValue
                : safe(fallbackSessionId);
        long timestamp = metadata.get("timestamp") instanceof Number number
                ? number.longValue()
                : System.currentTimeMillis();
        return new ReviewRecord(
                recordId,
                safe((String) metadata.get("reviewId")),
                sessionId,
                safe(doc.getText()),
                projectId,
                timestamp
        );
    }

    private void cacheRecord(ReviewRecord record, Map<String, Object> metadata) {
        if (record == null || record.id() == null || record.id().isBlank()) {
            return;
        }
        localCache.put(record.id(), new CachedReviewEntry(record, Map.copyOf(metadata == null ? Map.of() : metadata)));
    }

    private boolean matchesProject(String recordProjectId, String projectId) {
        return projectId == null || projectId.isBlank() || Objects.equals(projectId, recordProjectId);
    }

    private boolean matchesSession(String recordSessionId, String sessionId) {
        return sessionId == null || sessionId.isBlank() || Objects.equals(sessionId, recordSessionId);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private record CachedReviewEntry(ReviewRecord record, Map<String, Object> metadata) {
    }
}
