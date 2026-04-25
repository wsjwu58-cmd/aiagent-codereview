package com.heima.codereview.rag.vector;

import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.rag.model.ReviewRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final String REVIEW_SOURCE_TYPE = "review";

    private final MilvusVectorStore vectorStore;
    private final MilvusHybridRepository hybridRepository;
    private final String collectionName;
    private final int defaultTopK = 5;
    private final Map<String, CachedReviewEntry> localCache = new ConcurrentHashMap<>();

    public MilvusRepository(MilvusVectorStore vectorStore,
                            ObjectProvider<MilvusHybridRepository> hybridRepositoryProvider,
                            @Value("${spring.ai.vectorstore.milvus.collection-name:code_review_knowledge}") String collectionName) {
        this.vectorStore = vectorStore;
        this.hybridRepository = hybridRepositoryProvider.getIfAvailable();
        this.collectionName = collectionName;
        log.info("MilvusRepository initialized. collection={}", collectionName);
    }

    public void insert(String collectionName, ReviewRecord record) {
        insert(collectionName, record, Map.of());
    }

    public void insert(String collectionName, ReviewRecord record, Map<String, Object> metadata) {
        try {
            Map<String, Object> docMetadata = new LinkedHashMap<>();
            docMetadata.put("sourceType", REVIEW_SOURCE_TYPE);
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
            cacheRecord(toReviewRecord(doc, record.projectId(), record.sessionId()), docMetadata, documentId);
            if (hybridRepository != null) {
                hybridRepository.insert(record, docMetadata);
            }
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
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query == null ? "" : query)
                    .topK(topK > 0 ? topK : defaultTopK);
            String filterExpression = buildFilterExpression(projectId, sessionId);
            if (!filterExpression.isBlank()) {
                builder.filterExpression(filterExpression);
            }
            SearchRequest request = builder.build();

            List<Document> docs = vectorStore.similaritySearch(request);
            if (docs.isEmpty() && !filterExpression.isBlank()) {
                docs = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query == null ? "" : query)
                        .topK(topK > 0 ? topK : defaultTopK)
                        .build());
            }
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
                cacheRecord(record, doc.getMetadata(), doc.getId());
            }
            return results;
        } catch (Exception e) {
            log.error("Vector search failed. collection={}, query={}", this.collectionName, query, e);
            return List.of();
        }
    }

    public List<ReviewRecord> hybridSearch(String query, String projectId, String sessionId, int topK) {
        if (hybridRepository == null || !hybridRepository.isEnabled()) {
            return List.of();
        }
        return hybridRepository.search(query, projectId, sessionId, topK);
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

    public void deleteByReviewId(String reviewId) {
        if (reviewId == null || reviewId.isBlank()) {
            return;
        }
        List<String> documentIds = localCache.values().stream()
                .filter(entry -> Objects.equals(reviewId, entry.record().reviewId()))
                .map(CachedReviewEntry::documentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        deleteDocuments(documentIds);
        if (hybridRepository != null) {
            hybridRepository.deleteByReviewId(reviewId);
        }
        localCache.entrySet().removeIf(entry -> Objects.equals(reviewId, entry.getValue().record().reviewId()));
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

    private void cacheRecord(ReviewRecord record, Map<String, Object> metadata, String documentId) {
        if (record == null || record.id() == null || record.id().isBlank()) {
            return;
        }
        localCache.put(record.id(), new CachedReviewEntry(record, Map.copyOf(metadata == null ? Map.of() : metadata), documentId));
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

    private String buildFilterExpression(String projectId, String sessionId) {
        List<String> filters = new ArrayList<>();
        filters.add("sourceType == '" + REVIEW_SOURCE_TYPE + "'");
        if (projectId != null && !projectId.isBlank()) {
            filters.add("projectId == '" + escapeFilter(projectId) + "'");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            filters.add("sessionId == '" + escapeFilter(sessionId) + "'");
        }
        return String.join(" && ", filters);
    }

    private String escapeFilter(String value) {
        return safe(value).replace("\\", "\\\\").replace("'", "\\'");
    }

    private void deleteDocuments(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(documentIds);
        } catch (Exception e) {
            log.warn("Failed to delete review documents from vector store. ids={}, reason={}", documentIds, e.getMessage());
        }
    }

    private record CachedReviewEntry(ReviewRecord record, Map<String, Object> metadata, String documentId) {
    }
}
