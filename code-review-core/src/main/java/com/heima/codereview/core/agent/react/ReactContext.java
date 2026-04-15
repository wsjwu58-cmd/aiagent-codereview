package com.heima.codereview.core.agent.react;

import com.heima.codereview.core.agent.conversational.ConversationContext;

public record ReactContext(String userMessage, ConversationContext conversationContext) {
}
