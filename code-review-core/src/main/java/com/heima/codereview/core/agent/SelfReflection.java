package com.heima.codereview.core.agent;

import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.ReflectionResult;
import com.heima.codereview.core.agent.planning.SubTask;

public interface SelfReflection {

    ReflectionResult reflect(IntentAnalysisResult originalIntent,
                             SubTask task,
                             String executionResult,
                             ConversationContext context);
}
