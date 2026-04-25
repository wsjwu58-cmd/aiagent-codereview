package com.heima.codereview.core.agent.reflection;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class KeywordReflectionEvaluator implements ReflectionEvaluator {

    private static final String DEFAULT_GAP = "The current answer is incomplete or lacks evidence.";

    @Override
    public ReflectionEvaluation evaluate(ReflectionContext context) {
        String normalized = context == null || context.executionResult() == null
                ? ""
                : context.executionResult().toLowerCase(Locale.ROOT);
        if (requiresFollowUp(normalized)) {
            return ReflectionEvaluation.gap(
                    normalized.isBlank() ? 0.2 : 0.45,
                    DEFAULT_GAP,
                    List.of("Close the remaining gap with more evidence.")
            );
        }
        return ReflectionEvaluation.satisfied(0.9);
    }

    private boolean requiresFollowUp(String normalized) {
        return normalized.isBlank()
                || normalized.contains("insufficient")
                || normalized.contains("缺少")
                || normalized.contains("无法")
                || normalized.contains("unavailable")
                || normalized.contains("需要更多");
    }
}
