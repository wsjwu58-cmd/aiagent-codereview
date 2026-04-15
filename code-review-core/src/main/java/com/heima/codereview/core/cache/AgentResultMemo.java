package com.heima.codereview.core.cache;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class AgentResultMemo {

    private static final long TTL_MILLIS = 30L * 60 * 1000;

    private final Map<String, MemoEntry> memo = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getMemoizedResult(String agentId, String inputKey, Supplier<T> executor) {
        String memoKey = agentId + ":" + inputKey;
        MemoEntry old = memo.get(memoKey);
        long now = Instant.now().toEpochMilli();
        if (old != null && old.expireAt > now) {
            return (T) old.result;
        }
        T result = executor.get();
        memo.put(memoKey, new MemoEntry(result, now + TTL_MILLIS));
        return result;
    }

    private record MemoEntry(Object result, long expireAt) {
    }
}
