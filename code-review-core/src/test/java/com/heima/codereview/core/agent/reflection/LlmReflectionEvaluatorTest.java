package com.heima.codereview.core.agent.reflection;

import com.heima.codereview.core.agent.AgentTextGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmReflectionEvaluatorTest {

    @Test
    void evaluateParsesJsonResponseFromModel() {
        LlmReflectionEvaluator evaluator = new LlmReflectionEvaluator(
                new StubTextGenerator("""
                        ```json
                        {
                          "satisfied": false,
                          "confidence": 0.72,
                          "gapDescription": "需要补充证据",
                          "improvementSuggestions": ["读取本地文件", "补充安全结论"]
                        }
                        ```
                        """,
                        true
                )
        );

        ReflectionEvaluator.ReflectionEvaluation evaluation = evaluator.evaluate(context("已有初步结论"));

        assertFalse(evaluation.satisfied());
        assertEquals(0.72, evaluation.confidence(), 0.0001);
        assertEquals("需要补充证据", evaluation.gapDescription());
        assertEquals(List.of("读取本地文件", "补充安全结论"), evaluation.improvementSuggestions());
    }

    @Test
    void evaluateDisablesToolCallbacksWhenCallingModel() {
        StubTextGenerator textGenerator = new StubTextGenerator(
                "{\"satisfied\":true,\"confidence\":0.91,\"gapDescription\":\"\",\"improvementSuggestions\":[]}",
                true
        );
        LlmReflectionEvaluator evaluator = new LlmReflectionEvaluator(textGenerator);

        evaluator.evaluate(context("完整回答"));

        assertEquals(Boolean.TRUE, textGenerator.context.get("disableToolCallbacks"));
        assertEquals("reflection-evaluator", textGenerator.context.get("scene"));
    }

    @Test
    void evaluateFallsBackWhenModelUnavailable() {
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        LlmReflectionEvaluator evaluator = new LlmReflectionEvaluator(
                new StubTextGenerator("", false),
                context -> {
                    fallbackCalled.set(true);
                    return ReflectionEvaluator.ReflectionEvaluation.gap(0.3, "fallback", List.of("fallback suggestion"));
                }
        );

        ReflectionEvaluator.ReflectionEvaluation evaluation = evaluator.evaluate(context("完整回答"));

        assertTrue(fallbackCalled.get());
        assertFalse(evaluation.satisfied());
        assertEquals("fallback", evaluation.gapDescription());
    }

    @Test
    void evaluateFallsBackWhenModelReturnsInvalidJson() {
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        LlmReflectionEvaluator evaluator = new LlmReflectionEvaluator(
                new StubTextGenerator("not-json", true),
                context -> {
                    fallbackCalled.set(true);
                    return ReflectionEvaluator.ReflectionEvaluation.satisfied(0.88);
                }
        );

        ReflectionEvaluator.ReflectionEvaluation evaluation = evaluator.evaluate(context("完整回答"));

        assertTrue(fallbackCalled.get());
        assertTrue(evaluation.satisfied());
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

    private static class StubTextGenerator implements AgentTextGenerator {
        private final String response;
        private final boolean available;
        private Map<String, Object> context = Map.of();

        private StubTextGenerator(String response, boolean available) {
            this.response = response;
            this.available = available;
        }

        @Override
        public String generate(String agentName, String instruction, String input, Map<String, Object> context) {
            this.context = context;
            return response;
        }

        @Override
        public boolean available() {
            return available;
        }
    }
}
