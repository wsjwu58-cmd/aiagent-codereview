package com.heima.codereview.rag.retrieval;

import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.vector.MilvusRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HybridSearch {

    private static final int KEYWORD_CANDIDATE_LIMIT = 200;
    private static final double BM25_K1 = 1.2d;
    private static final double BM25_B = 0.75d;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[a-zA-Z0-9_./#:-]+");

    private final MilvusRepository milvusRepository;

    public HybridSearch(MilvusRepository milvusRepository) {
        this.milvusRepository = milvusRepository;
    }

    public List<ReviewRecord> search(String query, String projectId, String sessionId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        List<ReviewRecord> semanticResults = milvusRepository.search(query, projectId, sessionId, Math.max(limit * 2, limit));
        List<ReviewRecord> lexicalResults = keywordSearch(query, projectId, sessionId, Math.max(limit * 2, limit));

        Map<String, Double> fusedScores = new HashMap<>();
        Map<String, ReviewRecord> merged = new LinkedHashMap<>();
        applyRrf(semanticResults, fusedScores, merged, 1.0d);
        applyRrf(lexicalResults, fusedScores, merged, 1.15d);

        return fusedScores.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .map(entry -> merged.get(entry.getKey()))
                .limit(limit)
                .toList();
    }

    private List<ReviewRecord> keywordSearch(String query, String projectId, String sessionId, int topK) {
        List<String> queryTerms = extractTerms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<ReviewRecord> candidates = milvusRepository.cachedRecords(projectId, sessionId).stream()
                .limit(KEYWORD_CANDIDATE_LIMIT)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredRecord> scored = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        List<DocumentTermVector> vectors = new ArrayList<>();
        double avgDocLength = 0.0d;

        for (ReviewRecord candidate : candidates) {
            Map<String, Integer> termFreq = buildTermFrequency(candidate.content());
            int docLength = termFreq.values().stream().mapToInt(Integer::intValue).sum();
            if (docLength == 0) {
                continue;
            }
            vectors.add(new DocumentTermVector(candidate, termFreq, docLength));
            avgDocLength += docLength;
            for (String term : queryTerms) {
                if (termFreq.containsKey(term)) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        }

        if (vectors.isEmpty()) {
            return List.of();
        }
        avgDocLength = avgDocLength / vectors.size();

        for (DocumentTermVector vector : vectors) {
            double score = 0.0d;
            for (String term : queryTerms) {
                Integer frequency = vector.termFrequency().get(term);
                if (frequency == null || frequency <= 0) {
                    continue;
                }
                int df = Math.max(1, documentFrequency.getOrDefault(term, 1));
                double idf = Math.log1p((vectors.size() - df + 0.5d) / (df + 0.5d));
                double denominator = frequency + BM25_K1 * (1 - BM25_B + BM25_B * (vector.docLength() / Math.max(avgDocLength, 1.0d)));
                score += idf * (frequency * (BM25_K1 + 1)) / denominator;
            }
            String normalizedContent = normalize(vector.record().content());
            String normalizedQuery = normalize(query);
            if (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery)) {
                score += 0.8d;
            }
            if (score > 0) {
                scored.add(new ScoredRecord(vector.record(), score));
            }
        }

        return scored.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .map(ScoredRecord::record)
                .limit(Math.max(1, topK))
                .toList();
    }

    private void applyRrf(List<ReviewRecord> results,
                          Map<String, Double> fusedScores,
                          Map<String, ReviewRecord> merged,
                          double weight) {
        for (int index = 0; index < results.size(); index++) {
            ReviewRecord record = results.get(index);
            merged.putIfAbsent(record.id(), record);
            double score = weight / (60.0d + index + 1);
            fusedScores.merge(record.id(), score, Double::sum);
        }
    }

    private Map<String, Integer> buildTermFrequency(String content) {
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : extractTerms(content)) {
            termFrequency.merge(term, 1, Integer::sum);
        }
        return termFrequency;
    }

    private List<String> extractTerms(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.codePoints().allMatch(Character::isIdeographic)) {
                if (token.length() <= 4) {
                    terms.add(token);
                }
                for (int index = 0; index + 1 < token.length(); index++) {
                    terms.add(token.substring(index, index + 2));
                }
                continue;
            }
            if (token.length() > 1) {
                terms.add(token);
            }
        }
        return terms;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replace('\r', ' ').replace('\n', ' ').trim();
    }

    private record DocumentTermVector(ReviewRecord record, Map<String, Integer> termFrequency, int docLength) {
    }

    private record ScoredRecord(ReviewRecord record, double score) {
    }
}
