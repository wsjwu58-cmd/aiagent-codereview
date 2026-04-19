package com.heima.codereview.core.agent.planning;

public record ExecutionNode(
        String taskId,
        IntentType intentType,
        String agentId,
        boolean success,
        String summary
) {
}
