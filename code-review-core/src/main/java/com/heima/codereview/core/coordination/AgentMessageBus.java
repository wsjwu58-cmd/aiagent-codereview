package com.heima.codereview.core.coordination;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface AgentMessageBus {

    void publish(String topic, AgentMessage message);

    void subscribe(String topic, Consumer<AgentMessage> callback);

    CompletableFuture<AgentMessage> request(String topic, AgentMessage message);
}
