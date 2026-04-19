package com.heima.codereview.core.agent.planning;

import com.heima.codereview.common.utils.IdUtils;

import java.util.ArrayList;
import java.util.List;

public class BacktrackingPlanner {

    private final int maxRetries;

    public BacktrackingPlanner() {
        this(3);
    }

    public BacktrackingPlanner(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean canRetry(SubTask task, ExecutionHistory history) {
        return history.failureCount(task) < maxRetries;
    }

    public List<SubTask> findAlternativePath(SubTask failedTask, IntentAnalysisResult intent) {
        List<SubTask> alternatives = new ArrayList<>();
        if (failedTask == null || intent == null) {
            return alternatives;
        }

        if (failedTask.intentType() == IntentType.ARCHITECTURE_ANALYSIS) {
            alternatives.add(failedTask.nextDepth(
                    IdUtils.withPrefix("task"),
                    IntentType.DOCUMENTATION_GENERATION,
                    "Generate architecture explanation",
                    "Summarize the current architecture and state the limitations when deeper analysis is unavailable.",
                    "documentation-specialist"
            ));
        } else if (failedTask.intentType() == IntentType.CODE_REVIEW) {
            alternatives.add(failedTask.nextDepth(
                    IdUtils.withPrefix("task"),
                    IntentType.GENERAL_CODING,
                    "Fallback coding guidance",
                    "Provide a conservative code-level explanation and next debugging steps when structured review cannot complete.",
                    "general-coding-agent"
            ));
        } else if (intent.supports(IntentType.KNOWLEDGE_RETRIEVAL)) {
            alternatives.add(failedTask.nextDepth(
                    IdUtils.withPrefix("task"),
                    IntentType.KNOWLEDGE_RETRIEVAL,
                    "Retrieve supporting context",
                    "Search for related norms, history, and long-term memory to unblock the failed task.",
                    "rag-specialist"
            ));
        }
        return alternatives;
    }
}
