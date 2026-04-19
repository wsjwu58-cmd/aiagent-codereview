package com.heima.codereview.rag;

import com.heima.codereview.common.model.norm.NormRecord;
import com.heima.codereview.common.model.norm.NormSummary;
import com.heima.codereview.common.model.norm.NormUploadResult;
import com.heima.codereview.common.model.norm.PdfPageContent;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.rag.embedding.PdfExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PdfRepository {

    private static final Logger log = LoggerFactory.getLogger(PdfRepository.class);
    private static final String SOURCE_TYPE = "norm";
    private static final int MAX_VECTOR_QUERY_LENGTH = 320;
    private static final int MAX_CHUNK_LENGTH = 800;

    private final MilvusVectorStore vectorStore;
    private final PdfExtractor pdfExtractor;
    private final Map<String, NormSummary> catalog = new ConcurrentHashMap<>();
    private final Map<String, List<NormRecord>> pageStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> fileDocumentIds = new ConcurrentHashMap<>();

    public PdfRepository(MilvusVectorStore vectorStore, PdfExtractor pdfExtractor) {
        this.vectorStore = vectorStore;
        this.pdfExtractor = pdfExtractor;
    }

    public NormUploadResult uploadPdf(InputStream pdfStream, String fileName, Map<String, Object> metadata) {
        String normFileId = IdUtils.compactUuid();
        long uploadedAt = System.currentTimeMillis();
        String projectId = metadata == null || metadata.get("projectId") == null ? "" : String.valueOf(metadata.get("projectId"));
        String description = metadata == null || metadata.get("description") == null ? "" : String.valueOf(metadata.get("description"));

        List<PdfPageContent> pages = pdfExtractor.extract(pdfStream);
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("PDF文件中未能提取到可用文本内容");
        }

        List<Document> documents = new ArrayList<>(pages.size() * 3);
        List<NormRecord> records = new ArrayList<>(pages.size());
        List<String> documentIds = new ArrayList<>();
        int pageIndex = 0;
        for (PdfPageContent page : pages) {
            String recordId = IdUtils.compactUuid();
            Map<String, Object> docMetadata = new LinkedHashMap<>();
            docMetadata.put("sourceType", SOURCE_TYPE);
            docMetadata.put("normFileId", normFileId);
            docMetadata.put("fileName", fileName);
            docMetadata.put("projectId", projectId);
            docMetadata.put("description", description);
            docMetadata.put("pageNumber", page.pageNumber());
            docMetadata.put("uploadedAt", uploadedAt);
            if (metadata != null) {
                metadata.forEach((key, value) -> {
                    if (value != null) {
                        docMetadata.putIfAbsent(key, value);
                    }
                });
            }

            records.add(new NormRecord(
                    recordId,
                    fileName,
                    page.pageNumber(),
                    safeText(page.text()),
                    safeText(page.summary()),
                    Map.copyOf(docMetadata)
            ));

            List<String> chunks = splitIntoChunks(safeText(page.text()), MAX_CHUNK_LENGTH);
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = IdUtils.compactUuid();
                documentIds.add(chunkId);
                Map<String, Object> chunkMeta = new LinkedHashMap<>(docMetadata);
                chunkMeta.put("chunkIndex", i);
                chunkMeta.put("totalChunks", chunks.size());
                chunkMeta.put("pageIndex", pageIndex);
                chunkMeta.put("normFileId", normFileId);
                documents.add(new Document(chunkId, chunks.get(i), chunkMeta));
            }
            pageIndex++;
        }

        pageStore.put(normFileId, List.copyOf(records));
        fileDocumentIds.put(normFileId, List.copyOf(documentIds));
        catalog.put(normFileId, new NormSummary(
                normFileId,
                fileName,
                projectId,
                description,
                pages.size(),
                uploadedAt
        ));

        try {
            vectorStore.add(documents);
        } catch (Exception e) {
            log.warn("PDF规范写入向量库失败，已仅保存内存索引。fileName={}, reason={}", fileName, e.getMessage());
        }

        return new NormUploadResult(normFileId, fileName, projectId, pages.size(), description, uploadedAt);
    }

    public List<NormRecord> searchNorms(String query, String projectId, int limit) {
        int realLimit = Math.max(1, limit);
        LinkedHashMap<String, NormRecord> results = new LinkedHashMap<>();
        String vectorQuery = sanitizeVectorQuery(query);

        if (!vectorQuery.isBlank()) {
            try {
                List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(vectorQuery)
                        .topK(Math.max(realLimit * 4, realLimit))
                        .build());
                for (Document doc : docs) {
                    NormRecord record = toNormRecord(doc);
                    if (!matchesProject(record.metadata(), projectId)) {
                        continue;
                    }
                    if (!SOURCE_TYPE.equals(String.valueOf(record.metadata().getOrDefault("sourceType", "")))) {
                        continue;
                    }
                    results.putIfAbsent(record.id(), record);
                    if (results.size() >= realLimit) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("检索PDF规范向量失败，尝试使用内存存储兜底。reason={}", e.getMessage());
            }
        }

        if (results.size() < realLimit) {
            String normalizedQuery = safeText(query).trim();
            pageStore.values().stream()
                    .flatMap((List<NormRecord> records) -> records.stream())
                    .filter(record -> matchesProject(record.metadata(), projectId))
                    .filter(record -> normalizedQuery.isBlank()
                            || record.fileName().contains(normalizedQuery)
                            || record.summary().contains(normalizedQuery)
                            || record.content().contains(normalizedQuery))
                    .sorted(Comparator.comparingLong((NormRecord record) -> asLong(record.metadata().get("uploadedAt"))).reversed())
                    .limit(realLimit)
                    .forEach(record -> results.putIfAbsent(record.id(), record));
        }

        return results.values().stream()
                .limit(realLimit)
                .toList();
    }

    public List<NormSummary> listNorms(String projectId) {
        return catalog.values().stream()
                .filter(item -> projectId == null || projectId.isBlank() || Objects.equals(projectId, item.projectId()))
                .sorted(Comparator.comparingLong(NormSummary::uploadedAt).reversed())
                .toList();
    }

    public void deleteNorm(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        catalog.remove(fileId);
        pageStore.remove(fileId);
        List<String> documentIds = fileDocumentIds.remove(fileId);
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(documentIds);
        } catch (Exception e) {
            log.warn("Delete PDF norm vectors failed. fileId={}, reason={}", fileId, e.getMessage());
        }
    }

    private NormRecord toNormRecord(Document document) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        int pageNumber = metadata.get("pageNumber") instanceof Number number ? number.intValue() : 1;
        return new NormRecord(
                safeText(document.getId()),
                String.valueOf(metadata.getOrDefault("fileName", "")),
                pageNumber,
                safeText(document.getText()),
                summarize(document.getText()),
                Map.copyOf(metadata)
        );
    }

    private boolean matchesProject(Map<String, Object> metadata, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return true;
        }
        return Objects.equals(projectId, String.valueOf(metadata.getOrDefault("projectId", "")));
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String summarize(String text) {
        String normalized = safeText(text).replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
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

    private List<String> splitIntoChunks(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (text.length() <= maxLength) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            if (end < text.length()) {
                int breakPoint = text.lastIndexOf('\n', end);
                if (breakPoint > start + maxLength / 2) {
                    end = breakPoint + 1;
                } else {
                    breakPoint = text.lastIndexOf('。', end);
                    if (breakPoint > start + maxLength / 2) {
                        end = breakPoint + 1;
                    }
                }
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}

