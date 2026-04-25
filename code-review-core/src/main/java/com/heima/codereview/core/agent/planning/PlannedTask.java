package com.heima.codereview.core.agent.planning;

import java.util.List;

/**
 * LLM规划器生成的任务结构
 * 包含任务的所有元信息
 */
public record PlannedTask(
    String id,                    // 任务唯一标识
    IntentType intentType,        // 任务意图类型
    String title,                 // 任务标题
    String description,           // 任务详细描述
    String agentId,              // 指定执行的Agent ID
    int priority,                 // 优先级 1-5（1最高）
    List<String> dependencies     // 依赖的任务ID列表
) {
    public PlannedTask {
        // 优先级边界检查
        if (priority < 1 || priority > 5) {
            priority = 3;
        }
        if (dependencies == null) {
            dependencies = List.of();
        }
    }
}
