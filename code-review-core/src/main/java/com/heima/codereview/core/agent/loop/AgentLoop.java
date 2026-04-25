package com.heima.codereview.core.agent.loop;

import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.ReactStreamListener;
import com.heima.codereview.core.agent.conversational.SpecialistExecutionResult;
import com.heima.codereview.core.agent.specialized.SpecializedAgent;

public interface AgentLoop {

    String loopType();

    SpecialistExecutionResult execute(LoopRequest request);

    record LoopRequest(
            SpecializedAgent agent,
            String taskPrompt,
            ConversationContext context,
            ReactStreamListener listener
    ) {
    }
}
