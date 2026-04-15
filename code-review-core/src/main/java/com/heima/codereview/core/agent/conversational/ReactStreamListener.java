package com.heima.codereview.core.agent.conversational;

import com.heima.codereview.core.agent.react.ReactResult;
import com.heima.codereview.core.agent.react.ThinkingStep;

public interface ReactStreamListener {

    default void onAgentStart(String agentId, String agentName) {
    }

    default void onStep(ThinkingStep step) {
    }

    default void onAgentComplete(SpecialistReport report) {
    }

    default void onComplete(ReactResult result) {
    }

    default void onError(String message, Throwable throwable) {
    }
}
