package com.heima.codereview.core.agent.planning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExecutionHistory {

    private final List<ExecutionNode> executedPath = new ArrayList<>();
    private final Set<String> failedPaths = new LinkedHashSet<>();
    private final Map<String, Integer> failureCount = new LinkedHashMap<>();

    public void recordSuccess(SubTask task, String agentId, String summary) {
        executedPath.add(new ExecutionNode(task.taskId(), task.intentType(), agentId, true, normalize(summary)));
    }

    public void recordFailure(SubTask task, String agentId, String reason) {
        executedPath.add(new ExecutionNode(task.taskId(), task.intentType(), agentId, false, normalize(reason)));
        failedPaths.add(pathKey(task));
        failureCount.merge(pathKey(task), 1, Integer::sum);
    }

    public boolean isPathFailed(SubTask task) {
        return failedPaths.contains(pathKey(task));
    }

    public int failureCount(SubTask task) {
        return failureCount.getOrDefault(pathKey(task), 0);
    }

    public List<ExecutionNode> executedPath() {
        return List.copyOf(executedPath);
    }

    private String pathKey(SubTask task) {
        return task.intentType() + ":" + normalize(task.title()) + ":" + normalize(task.preferredAgentId());
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
