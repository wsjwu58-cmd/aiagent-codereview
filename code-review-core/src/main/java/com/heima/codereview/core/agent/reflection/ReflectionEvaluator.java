package com.heima.codereview.core.agent.reflection;

import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.SubTask;

import java.util.List;

public interface ReflectionEvaluator {

    ReflectionEvaluation evaluate(ReflectionContext context);

    record ReflectionContext(
            String specialistId,
            IntentAnalysisResult originalIntent,
            SubTask task,
            String executionResult,
            ConversationContext conversationContext
    ) {
    }

    record ReflectionEvaluation(
            boolean satisfied,
            double confidence,
            String gapDescription,
            List<String> improvementSuggestions
    ) {

        public ReflectionEvaluation {
            confidence = Math.max(0.0, Math.min(confidence, 1.0));
            gapDescription = gapDescription == null ? "" : gapDescription;
            improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
        }

        public static ReflectionEvaluation satisfied(double confidence) {
            return new ReflectionEvaluation(true, confidence, "", List.of());
        }

        public static ReflectionEvaluation gap(double confidence, String gapDescription, List<String> improvementSuggestions) {
            return new ReflectionEvaluation(false, confidence, gapDescription, improvementSuggestions);
        }
    }
}
