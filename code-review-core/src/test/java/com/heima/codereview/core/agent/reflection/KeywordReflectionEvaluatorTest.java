package com.heima.codereview.core.agent.reflection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordReflectionEvaluatorTest {

    private final KeywordReflectionEvaluator evaluator = new KeywordReflectionEvaluator();

    @Test
    void evaluateFindsGapForBlankOrInsufficientOutput() {
        assertFalse(evaluator.evaluate(context("")).satisfied());
        assertFalse(evaluator.evaluate(context("insufficient evidence")).satisfied());
        assertFalse(evaluator.evaluate(context("当前缺少关键上下文，无法确认")).satisfied());
    }

    @Test
    void evaluateSatisfiedForNormalOutput() {
        ReflectionEvaluator.ReflectionEvaluation evaluation = evaluator.evaluate(
                context("已经完成审查，并给出了证据和建议。")
        );

        assertTrue(evaluation.satisfied());
        assertTrue(evaluation.confidence() > 0.8);
    }

    private static ReflectionEvaluator.ReflectionContext context(String output) {
        return new ReflectionEvaluator.ReflectionContext(
                "test-specialist",
                null,
                null,
                output,
                null
        );
    }
}
