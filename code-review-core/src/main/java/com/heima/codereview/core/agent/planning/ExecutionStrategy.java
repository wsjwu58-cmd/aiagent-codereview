package com.heima.codereview.core.agent.planning;

/**
 * 任务执行策略枚举
 * 用于LLM规划器决定任务的执行方式
 */
public enum ExecutionStrategy {

    /** 所有任务并行执行（无依赖关系） */
    PARALLEL,

    /** 所有任务按顺序串行执行 */
    SEQUENTIAL,

    /** 部分并行 + 部分串行（根据任务依赖关系） */
    MIXED
}
