package com.heima.codereview.core.agent.react;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReactDecision(
        String thought,
        Action action,
        String toolName,
        Map<String, Object> toolParams,
        String finalAnswer,
        String terminationReason
) {

    public ReactDecision {
        toolParams = toolParams == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(toolParams));
        thought = thought == null ? "" : thought;
        toolName = toolName == null ? "" : toolName;
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
        terminationReason = terminationReason == null ? "" : terminationReason;
    }

    public static ReactDecision tool(String thought, String toolName, Map<String, Object> toolParams) {
        return new ReactDecision(thought, Action.TOOL, toolName, toolParams, "", "");
    }

    public static ReactDecision finish(String thought, String finalAnswer, String terminationReason) {
        return new ReactDecision(thought, Action.FINISH, "", Map.of(), finalAnswer, terminationReason);
    }

    public enum Action {
        TOOL,
        FINISH
    }
}
