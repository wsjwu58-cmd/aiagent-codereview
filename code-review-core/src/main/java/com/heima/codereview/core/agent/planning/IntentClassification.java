package com.heima.codereview.core.agent.planning;

import java.util.List;

/**
 * LLM意图分类器的输出结果
 * 包含主意图、候选意图、置信度和复杂度评估
 */
public record IntentClassification(
    IntentType primary,                      // 主意图
    List<IntentType> candidates,             // 候选意图列表
    double confidence,                        // 置信度 0.0-1.0
    String rationale,                         // 分类理由
    boolean requiresRepository,              // 是否需要仓库上下文
    String complexity                        // 复杂度评估 LOW|MEDIUM|HIGH
) {
    /**
     * 从关键词快速预筛结果创建分类
     * 用于LLM不可用或简单请求场景
     */
    public static IntentClassification fromQuickResults(List<IntentType> intents) {
        IntentType primary = intents.isEmpty() ? IntentType.GENERAL_CODING : intents.get(0);
        return new IntentClassification(
            primary, intents, 0.5, "Quick keyword-based classification",
            false, "LOW"
        );
    }
}
