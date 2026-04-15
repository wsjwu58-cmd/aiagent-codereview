package com.heima.codereview.core.enhance;

import com.heima.codereview.common.model.review.SimilarCodeGroup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CodeSimilarityDetector {

    public List<SimilarCodeGroup> detect(String codeContent) {
        if (codeContent == null || codeContent.isBlank()) {
            return List.of();
        }

        String[] lines = codeContent.split("\\r?\\n");
        Map<String, Integer> counter = new HashMap<>();
        for (String line : lines) {
            String normalized = normalize(line);
            if (normalized.length() < 12) {
                continue;
            }
            counter.put(normalized, counter.getOrDefault(normalized, 0) + 1);
        }

        List<SimilarCodeGroup> groups = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<String, Integer> item : counter.entrySet()) {
            if (item.getValue() >= 2) {
                groups.add(new SimilarCodeGroup(
                        "group-" + idx++,
                        Math.min(1.0, item.getValue() * 0.2),
                        List.of(item.getKey())
                ));
            }
        }
        return groups;
    }

    private String normalize(String line) {
        return line.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
