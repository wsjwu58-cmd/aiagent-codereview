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

    private final MilvusVectorStore vectorStore;
    private final PdfExtractor pdfExtractor;
    private final Map<String, NormSummary> catalog = new ConcurrentHashMap<>();
    private final Map<String, List<NormRecord>> pageStore = new ConcurrentHashMap<>();

    public PdfRepository(MilvusVectorStore vectorStore, PdfExtractor pdfExtractor) {
        this.vectorStore = vectorStore;
        this.pdfExtractor = pdfExtractor;
    }

    public NormUploadResult uploadPdf(InputStream pdfStream, String fileName, Map<String, Object> metadata) {
        String normFileId = IdUtils.compactWithPrefix("normfile", 32);
        long uploadedAt = System.currentTimeMillis();
        String projectId = metadata == null || metadata.get("projectId") == null ? "" : String.valueOf(metadata.get("projectId"));
        String description = metadata == null || metadata.get("description") == null ? "" : String.valueOf(metadata.get("description"));

        List<PdfPageContent> pages = pdfExtractor.extract(pdfStream);
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("鏈兘浠?PDF 涓彁鍙栧埌鍙敤鏂囨湰鍐呭");
        }

        List<Document> documents = new ArrayList<>(pages.size());
        List<NormRecord> records = new ArrayList<>(pages.size());
        for (PdfPageContent page : pages) {
            String recordId = IdUtils.compactWithPrefix("norm", 32);
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
            documents.add(new Document(recordId, safeText(page.text()), docMetadata));
        }

        pageStore.put(normFileId, List.copyOf(records));
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
            log.warn("PDF 瑙勮寖鍐欏叆鍚戦噺搴撳け璐ワ紝宸蹭粎淇濈暀鍐呭瓨绱㈠紩銆俧ileName={}, reason={}", fileName, e.getMessage());
        }

        return new NormUploadResult(normFileId, fileName, projectId, pages.size(), description, uploadedAt);
    }

    public List<NormRecord> searchNorms(String query, String projectId, int limit) {
        int realLimit = Math.max(1, limit);
        LinkedHashMap<String, NormRecord> results = new LinkedHashMap<>();

        if (query != null && !query.isBlank()) {
            try {
                List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
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
                log.warn("妫€绱?PDF 瑙勮寖鍚戦噺澶辫触锛屽紑濮嬩娇鐢ㄥ唴瀛樼储寮曞厹搴曘€俽eason={}", e.getMessage());
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
}

