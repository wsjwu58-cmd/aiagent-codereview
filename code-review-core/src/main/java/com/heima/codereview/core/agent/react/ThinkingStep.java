package com.heima.codereview.core.agent.react;

public record ThinkingStep(
        String type,
        String agentId,
        String agentName,
        String content,
        String toolName,
        String input,
        String output,
        long timestamp
) {

    public static ThinkingStep thinking(String agentId, String agentName, String content) {
        return new ThinkingStep("THINKING", agentId, agentName, content, "", "", "", System.currentTimeMillis());
    }

    public static ThinkingStep toolCall(String agentId, String agentName, String toolName, String input, String output) {
        return new ThinkingStep("TOOL_CALL", agentId, agentName, "", toolName, input, output, System.currentTimeMillis());
    }

    public static ThinkingStep observation(String agentId, String agentName, String content) {
        return new ThinkingStep("OBSERVATION", agentId, agentName, content, "", "", "", System.currentTimeMillis());
    }
}
