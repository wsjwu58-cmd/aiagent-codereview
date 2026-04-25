package com.heima.codereview.core.agent.planning;

import java.util.List;

/**
 * LLM任务规划器的输出结果
 * 包含规划的任务列表和执行策略
 */
public record PlannedTasks(
    List<PlannedTask> tasks,      // 规划的任务列表
    ExecutionStrategy strategy,    // 执行策略
    String rationale              // 规划理由说明
) {
    public PlannedTasks {
        if (tasks == null || tasks.isEmpty()) {
            tasks = List.of();
        }
        if (strategy == null) {
            strategy = ExecutionStrategy.SEQUENTIAL;
        }
    }

    /** 返回空规划结果 */
    public static PlannedTasks empty() {
        return new PlannedTasks(List.of(), ExecutionStrategy.SEQUENTIAL, "");
    }
}
