package com.heima.codereview.core.coordination;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class MessageBus implements AgentMessageBus {
    private final Map<String, AgentMessage> messages = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<AgentMessage>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(String key, AgentMessage value) {
        messages.put(key, value);
        subscribers.getOrDefault(key, new CopyOnWriteArrayList<>()).forEach(callback -> callback.accept(value));
    }

    @SuppressWarnings("unchecked")
    public <T> T consume(String key) {
        return (T) messages.remove(key);
    }

    @Override
    public void subscribe(String topic, Consumer<AgentMessage> callback) {
        subscribers.computeIfAbsent(topic, ignored -> new CopyOnWriteArrayList<>()).add(callback);
    }

    @Override
    public CompletableFuture<AgentMessage> request(String topic, AgentMessage message) {
        CompletableFuture<AgentMessage> future = new CompletableFuture<>();
        subscribe(topic, future::complete);
        publish(topic, message);
        return future;
    }
}
