package com.heima.codereview.core.cache;

import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.utils.HashUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReviewResultCache {

    private static final long TTL_MILLIS = 7L * 24 * 3600 * 1000;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public String generateCacheKey(String code, String language, String rulesVersion) {
        return HashUtils.sha256(code + ":" + language + ":" + rulesVersion);
    }

    public ReviewReport get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt < Instant.now().toEpochMilli()) {
            cache.remove(key);
            return null;
        }
        return entry.report;
    }

    public void put(String key, ReviewReport report) {
        cache.put(key, new CacheEntry(report, Instant.now().toEpochMilli() + TTL_MILLIS));
    }

    private record CacheEntry(ReviewReport report, long expireAt) {
    }
}
