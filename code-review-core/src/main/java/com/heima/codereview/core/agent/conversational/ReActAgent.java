package com.heima.codereview.core.agent.conversational;

import com.heima.codereview.core.agent.BaseAgent;
import com.heima.codereview.core.agent.FlowAgent;
import com.heima.codereview.core.agent.react.ReactResult;
import org.springframework.stereotype.Component;

@Component
public class ReActAgent extends BaseAgent {

    private final FlowAgent flowAgent;

    public ReActAgent(FlowAgent flowAgent) {
        this.flowAgent = flowAgent;
    }

    @Override
    public String getName() {
        return "深度分析 Agent";
    }

    public ReactResult analyze(String userMessage, ConversationContext context, ReactStreamListener listener) {
        return flowAgent.executeConversation(userMessage, context, listener);
    }
}
