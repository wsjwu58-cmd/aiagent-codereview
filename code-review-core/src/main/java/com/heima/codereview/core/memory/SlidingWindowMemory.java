package com.heima.codereview.core.memory;

import com.heima.codereview.common.model.chat.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SlidingWindowMemory implements ChatMemory {

    private static final int WINDOW_SIZE = 20;
    private final ConcurrentHashMap<String, Deque<ChatMessage>> memoryStore = new ConcurrentHashMap<>();

    @Override
    public void append(String sessionId, ChatMessage message) {
        Deque<ChatMessage> deque = memoryStore.computeIfAbsent(sessionId, key -> new ConcurrentLinkedDeque<>());
        deque.addLast(message);
        while (deque.size() > WINDOW_SIZE) {
            deque.pollFirst();
        }
    }

    @Override
    public List<ChatMessage> recent(String sessionId) {
        Deque<ChatMessage> deque = memoryStore.get(sessionId);
        if (deque == null) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }
}
