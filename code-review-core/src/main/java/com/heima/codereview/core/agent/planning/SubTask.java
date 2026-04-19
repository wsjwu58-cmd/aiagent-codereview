package com.heima.codereview.core.agent.planning;

import java.util.LinkedHashMap;
import java.util.Map;

public record SubTask(
        String taskId,
        IntentType intentType,
        String title,
        String description,
        String preferredAgentId,
        int depth,
        String sourceTaskId,
        Map<String, Object> attributes
) {

    public SubTask {
        taskId = taskId == null ? "" : taskId;
        intentType = intentType == null ? IntentType.GENERAL_CODING : intentType;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        preferredAgentId = preferredAgentId == null ? "" : preferredAgentId;
        depth = Math.max(depth, 0);
        sourceTaskId = sourceTaskId == null ? "" : sourceTaskId;
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static SubTask of(String taskId,
                             IntentType intentType,
                             String title,
                             String description,
                             String preferredAgentId,
                             int depth,
                             String sourceTaskId) {
        return new SubTask(taskId, intentType, title, description, preferredAgentId, depth, sourceTaskId, Map.of());
    }

    public SubTask nextDepth(String nextTaskId, IntentType nextIntent, String nextTitle, String nextDescription, String nextAgentId) {
        return new SubTask(nextTaskId, nextIntent, nextTitle, nextDescription, nextAgentId, depth + 1, taskId, attributes);
    }
}
