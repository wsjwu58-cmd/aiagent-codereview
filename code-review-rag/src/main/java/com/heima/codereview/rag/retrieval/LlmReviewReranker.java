package com.heima.codereview.rag.retrieval;

import com.heima.codereview.rag.model.ReviewRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmReviewReranker implements ReviewReranker {

    private static final Logger log = LoggerFactory.getLogger(LlmReviewReranker.class);
    private static final Pattern SCORE_LINE = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*[:|,]\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?)\\b.*$");
    private static final int MAX_CANDIDATES = 18;
    private static final int MAX_CONTENT_PREVIEW = 520;

    private final ChatClient chatClient;
    private final boolean enabled;

    public LlmReviewReranker(ChatClient.Builder chatClientBuilder,
                             @Value("${code-review.rag.rerank.enabled:true}") boolean enabled) {
        this.chatClient = chatClientBuilder.build();
        this.enabled = enabled;
    }

    @Override
    public List<ReviewRecord> rerank(String query, List<ReviewRecord> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        List<ReviewRecord> uniqueCandidates = dedupe(candidates).stream()
                .limit(MAX_CANDIDATES)
                .toList();
        if (!enabled || uniqueCandidates.size() <= 1) {
            return heuristicRerank(query, uniqueCandidates, limit);
        }
        try {
            Map<String, Double> scores = askModel(query, uniqueCandidates);
            if (scores.isEmpty()) {
                return heuristicRerank(query, uniqueCandidates, limit);
            }
            return uniqueCandidates.stream()
                    .sorted(Comparator
                            .comparingDouble((ReviewRecord record) -> scores.getOrDefault(record.id(), heuristicScore(query, record)))
                            .reversed())
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            log.warn("RAG rerank failed, using heuristic rerank. query={}, reason={}", preview(query), e.getMessage());
            return heuristicRerank(query, uniqueCandidates, limit);
        }
    }

    private Map<String, Double> askModel(String query, List<ReviewRecord> candidates) {
        String response = chatClient.prompt()
                .system("""
                        You rerank code-review RAG candidates.
                        Return only lines in this exact format: candidateId|score
                        score must be between 0 and 1. Higher means the candidate is more useful for answering the query.
                        Prefer project-specific, concrete, recent, and directly matching review evidence.
                        """)
                .user("""
                        Query:
                        %s

                        Candidates:
                        %s
                        """.formatted(query, formatCandidates(candidates)))
                .call()
                .content();
        return parseScores(response);
    }

    private String formatCandidates(List<ReviewRecord> candidates) {
        StringBuilder builder = new StringBuilder();
        for (ReviewRecord candidate : candidates) {
            builder.append(candidate.id()).append("|reviewId=").append(candidate.reviewId())
                    .append("|projectId=").append(candidate.projectId())
                    .append("|sessionId=").append(candidate.sessionId())
                    .append("|content=").append(preview(candidate.content(), MAX_CONTENT_PREVIEW))
                    .append('\n');
        }
        return builder.toString();
    }

    private Map<String, Double> parseScores(String response) {
        Map<String, Double> scores = new HashMap<>();
        if (response == null || response.isBlank()) {
            return scores;
        }
        for (String line : response.split("\\R")) {
            Matcher matcher = SCORE_LINE.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            double score = Double.parseDouble(matcher.group(2));
            scores.put(matcher.group(1), Math.max(0.0d, Math.min(1.0d, score)));
        }
        return scores;
    }

    private List<ReviewRecord> heuristicRerank(String query, List<ReviewRecord> candidates, int limit) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble((ReviewRecord record) -> heuristicScore(query, record)).reversed())
                .limit(limit)
                .toList();
    }

    private double heuristicScore(String query, ReviewRecord record) {
        List<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) {
            return 0.0d;
        }
        String content = normalize(record.content());
        double score = 0.0d;
        for (String term : queryTerms) {
            if (content.contains(term)) {
                score += term.length() >= 4 ? 2.0d : 1.0d;
            }
        }
        String normalizedQuery = normalize(query);
        if (!normalizedQuery.isBlank() && content.contains(normalizedQuery)) {
            score += 3.0d;
        }
        return score / Math.max(1.0d, queryTerms.size());
    }

    private List<ReviewRecord> dedupe(List<ReviewRecord> candidates) {
        Map<String, ReviewRecord> records = new LinkedHashMap<>();
        for (ReviewRecord candidate : candidates) {
            if (candidate != null && candidate.id() != null && !candidate.id().isBlank()) {
                records.putIfAbsent(candidate.id(), candidate);
            }
        }
        return new ArrayList<>(records.values());
    }

    private List<String> terms(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : normalized.split("[^\\p{IsHan}a-zA-Z0-9_./#:-]+")) {
            if (token.length() > 1) {
                result.add(token);
            }
        }
        return result;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String preview(String text) {
        return preview(text, 120);
    }

    private String preview(String text, int maxLength) {
        String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
