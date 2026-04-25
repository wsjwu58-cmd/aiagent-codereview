package com.heima.codereview.rag;

import com.heima.codereview.common.model.chat.ChatHistoryRecord;
import com.heima.codereview.common.model.chat.ChatMessage;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.rag.embedding.TextChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryRepository.class);
    private static final String SOURCE_TYPE = "chat_history";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_CHUNK_LENGTH = 380;
    private static final int CHUNK_OVERLAP = 60;
    private static final int MAX_VECTOR_QUERY_LENGTH = 320;

    private final MilvusVectorStore vectorStore;
    private final TextChunker textChunker;
    private final ConcurrentLinkedDeque<ChatHistoryRecord> localCache = new ConcurrentLinkedDeque<>();
    private final Map<String, List<String>> messageDocumentIds = new ConcurrentHashMap<>();

    public ChatHistoryRepository(MilvusVectorStore vectorStore, TextChunker textChunker) {
        this.vectorStore = vectorStore;
        this.textChunker = textChunker;
    }

    public void saveChatHistory(String sessionId, ChatMessage message) {
        ChatHistoryRecord record = new ChatHistoryRecord(
                IdUtils.compactWithPrefix("chat", 32),
                sessionId,
                safeText(message.role()),
                safeText(message.content()),
                message.timestamp(),
                List.of()
        );
        localCache.addFirst(record);
        while (localCache.size() > 500) {
            localCache.pollLast();
        }

        try {
            List<Document> docs = buildDocuments(record, message);
            if (!docs.isEmpty()) {
                messageDocumentIds.put(record.id(), docs.stream().map(Document::getId).toList());
                vectorStore.add(docs);
            }
        } catch (Exception e) {
            log.warn("保存对话历史到向量库失败，已降级为内存缓存。sessionId={}, reason={}", sessionId, e.getMessage());
        }
    }

    public List<ChatHistoryRecord> searchRelevant(String query, String currentSessionId, int limit) {
        int realLimit = Math.max(1, limit);
        LinkedHashMap<String, ChatHistoryRecord> merged = new LinkedHashMap<>();
        String vectorQuery = sanitizeVectorQuery(query);

        if (!vectorQuery.isBlank()) {
            try {
                List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(vectorQuery)
                        .topK(Math.max(realLimit * 3, realLimit))
                        .build());
                for (Document doc : docs) {
                    ChatHistoryRecord record = toRecord(doc);
                    if (currentSessionId != null && currentSessionId.equals(record.sessionId())) {
                        continue;
                    }
                    merged.putIfAbsent(record.id(), record);
                    if (merged.size() >= realLimit) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("检索对话历史向量失败，尝试使用本地缓存。reason={}", e.getMessage());
            }
        }

        if (merged.size() < realLimit) {
            String normalizedQuery = safeText(query).trim();
            localCache.stream()
                    .filter(item -> currentSessionId == null || !currentSessionId.equals(item.sessionId()))
                    .filter(item -> normalizedQuery.isBlank() || item.content().contains(normalizedQuery))
                    .sorted(Comparator.comparingLong(ChatHistoryRecord::timestamp).reversed())
                    .limit(realLimit)
                    .forEach(item -> merged.putIfAbsent(item.id(), item));
        }

        return merged.values().stream()
                .limit(realLimit)
                .toList();
    }

    public List<ChatHistoryRecord> listRecent(String sessionId, int limit) {
        return localCache.stream()
                .filter(item -> sessionId == null || sessionId.isBlank() || Objects.equals(sessionId, item.sessionId()))
                .sorted(Comparator.comparingLong(ChatHistoryRecord::timestamp).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public void deleteByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        localCache.removeIf(item -> Objects.equals(messageId, item.id()));
        List<String> documentIds = messageDocumentIds.remove(messageId);
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(documentIds);
        } catch (Exception e) {
            log.warn("Delete chat history vectors failed. messageId={}, reason={}", messageId, e.getMessage());
        }
    }

    private ChatHistoryRecord toRecord(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        long timestamp = metadata.get("timestamp") instanceof Number number
                ? number.longValue()
                : System.currentTimeMillis();
        String role = metadata.get("role") == null ? "" : String.valueOf(metadata.get("role"));
        String sessionId = metadata.get("sessionId") == null ? "" : String.valueOf(metadata.get("sessionId"));
        String messageId = metadata.get("messageId") instanceof String value && !value.isBlank()
                ? value
                : safeText(document.getId());
        return new ChatHistoryRecord(
                messageId,
                sessionId,
                role,
                safeText(document.getText()),
                timestamp,
                List.of()
        );
    }

    private List<Document> buildDocuments(ChatHistoryRecord record, ChatMessage message) {
        String header = "[%s] %s".formatted(
                safeText(message.role()),
                FORMATTER.format(Instant.ofEpochMilli(message.timestamp()))
        );
        List<String> chunks = textChunker.chunk(safeText(message.content()), MAX_CHUNK_LENGTH, CHUNK_OVERLAP, TextChunker.ChunkProfile.CHAT);
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<Document> docs = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            docs.add(new Document(
                    IdUtils.compactWithPrefix("chat", 32),
                    header + ": " + chunks.get(index),
                    Map.of(
                            "sourceType", SOURCE_TYPE,
                            "sessionId", safeText(record.sessionId()),
                            "role", safeText(record.role()),
                            "timestamp", record.timestamp(),
                            "messageId", record.id(),
                            "chunkIndex", index,
                            "chunkCount", chunks.size()
                    )
            ));
        }
        return docs;
    }

    private List<String> splitChunks(String text) {
        String normalized = safeText(text).replace("\r", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (normalized.length() <= MAX_CHUNK_LENGTH) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + MAX_CHUNK_LENGTH);
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String sanitizeVectorQuery(String text) {
        String normalized = safeText(text)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= MAX_VECTOR_QUERY_LENGTH) {
            return normalized;
        }
        int tailLength = Math.min(100, normalized.length() / 3);
        int headLength = Math.max(0, MAX_VECTOR_QUERY_LENGTH - tailLength - 5);
        return normalized.substring(0, headLength) + " ... " + normalized.substring(normalized.length() - tailLength);
    }
}
