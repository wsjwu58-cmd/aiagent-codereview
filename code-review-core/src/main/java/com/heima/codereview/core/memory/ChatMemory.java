package com.heima.codereview.core.memory;

import com.heima.codereview.common.model.chat.ChatMessage;

import java.util.List;

public interface ChatMemory {
    void append(String sessionId, ChatMessage message);

    List<ChatMessage> recent(String sessionId);
}
