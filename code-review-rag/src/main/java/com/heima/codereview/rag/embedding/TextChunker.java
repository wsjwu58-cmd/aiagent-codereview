package com.heima.codereview.rag.embedding;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize) {
        return chunk(text, chunkSize, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int realChunkSize = Math.max(100, chunkSize);
        int realOverlap = Math.max(0, Math.min(overlap, realChunkSize / 4));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + realChunkSize);
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - realOverlap);
        }
        return chunks;
    }
}
