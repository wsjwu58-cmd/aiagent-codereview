package com.heima.codereview.core.agent.react;

import java.util.List;

public record ReactState(
        List<ThinkingStep> thinkingSteps,
        List<ToolCallResult> toolResults,
        int iteration,
        int consecutiveNoProgress,
        String latestContent,
        boolean finalAnswerReady,
        String terminationReason
) {

    public ReactState {
        thinkingSteps = thinkingSteps == null ? List.of() : List.copyOf(thinkingSteps);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        latestContent = latestContent == null ? "" : latestContent;
        terminationReason = terminationReason == null ? "" : terminationReason;
    }

    public static ReactState initial() {
        return new ReactState(List.of(), List.of(), 0, 0, "", false, "");
    }

    public boolean hasToolCall(String toolName) {
        return toolResults.stream().anyMatch(item -> item != null && toolName.equals(item.toolName()));
    }

    public boolean hasToolCall(String toolName, String input) {
        return toolResults.stream().anyMatch(item -> item != null
                && toolName.equals(item.toolName())
                && input.equals(item.input()));
    }

    public boolean isStuck() {
        return consecutiveNoProgress >= 2;
    }

    public boolean hasMeaningfulContent() {
        return latestContent != null && !latestContent.isBlank();
    }
}
