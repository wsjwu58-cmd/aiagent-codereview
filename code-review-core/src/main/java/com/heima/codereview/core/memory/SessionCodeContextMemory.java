package com.heima.codereview.core.memory;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SessionCodeContextMemory {

    private static final int MAX_SESSION_COUNT = 200;
    private static final int MAX_CONTENT_LENGTH = 30000;

    private final ConcurrentHashMap<String, SessionCodeContext> store = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> order = new ConcurrentLinkedDeque<>();

    public void remember(String sessionId, SessionCodeContext context) {
        if (sessionId == null || sessionId.isBlank() || context == null || !context.hasContent()) {
            return;
        }
        SessionCodeContext normalized = new SessionCodeContext(
                safe(context.projectId()),
                safe(context.repoUrl()),
                safe(context.branch()),
                safe(context.language()),
                shorten(context.content()),
                context.updatedAt()
        );
        store.put(sessionId, normalized);
        order.remove(sessionId);
        order.addFirst(sessionId);
        while (order.size() > MAX_SESSION_COUNT) {
            String expiredSessionId = order.pollLast();
            if (expiredSessionId != null) {
                store.remove(expiredSessionId);
            }
        }
    }

    public Optional<SessionCodeContext> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(sessionId));
    }

    private String shorten(String text) {
        String normalized = safe(text).trim();
        if (normalized.length() <= MAX_CONTENT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTENT_LENGTH);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
