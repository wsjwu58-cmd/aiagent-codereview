package com.heima.codereview.core.agent.planning;

import java.util.List;

public record IntentAnalysisResult(
        IntentType primaryIntent,
        List<IntentType> candidateIntents,
        int complexityScore,
        boolean requiresRepositoryContext,
        boolean requiresReflection,
        boolean requiresBacktracking,
        String rationale
) {

    public IntentAnalysisResult {
        primaryIntent = primaryIntent == null ? IntentType.GENERAL_CODING : primaryIntent;
        candidateIntents = candidateIntents == null || candidateIntents.isEmpty()
                ? List.of(primaryIntent)
                : List.copyOf(candidateIntents);
        complexityScore = Math.max(complexityScore, 0);
        rationale = rationale == null ? "" : rationale;
    }

    public boolean isSimpleAnswer() {
        return primaryIntent == IntentType.SIMPLE_ANSWER;
    }

    public boolean supports(IntentType intentType) {
        return primaryIntent == intentType || candidateIntents.contains(intentType);
    }
}
