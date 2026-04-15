package com.heima.codereview.core.agent.conversational;

public interface ConversationalAgent {

    String respond(String userMessage, ConversationContext context);
}
