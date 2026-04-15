package com.heima.codereview.rag.cache;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmbeddingCache {

    private static final int MAX_SIZE = 1000;
    private final Map<String, List<Float>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                    return size() > MAX_SIZE;
                }
            });

    public List<Float> get(String text) {
        return cache.get(text);
    }

    public List<Float> putAndGet(String text, List<Float> vector) {
        List<Float> immutable = List.copyOf(vector);
        cache.put(text, immutable);
        return immutable;
    }

    public void clear() {
        cache.clear();
    }
}
