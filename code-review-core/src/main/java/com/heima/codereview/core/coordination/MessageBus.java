package com.heima.codereview.core.coordination;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageBus {
    private final Map<String, Object> messages = new ConcurrentHashMap<>();

    public void publish(String key, Object value) {
        messages.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T consume(String key) {
        return (T) messages.remove(key);
    }
}
