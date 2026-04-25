package com.heima.codereview.core.agent;

import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.SpecialistExecutionResult;
import com.heima.codereview.core.agent.conversational.SpecialistReport;
import com.heima.codereview.core.agent.loop.AgentLoop;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.planning.ReflectionResult;
import com.heima.codereview.core.agent.planning.SubTask;
import com.heima.codereview.core.agent.react.ReactContext;
import com.heima.codereview.core.agent.react.ToolCallResult;
import com.heima.codereview.core.agent.reflection.ReflectionEvaluator;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialistAgentTest {

    @Test
    void executeTaskDelegatesToAgentLoopAbstraction() {
        FakeSpecialistAgent specialist = new FakeSpecialistAgent();
        CapturingLoop loop = new CapturingLoop();
        ConversationContext context = context();
        SubTask task = SubTask.of(
                "task-1",
                IntentType.SECURITY_ANALYSIS,
                "Security check",
                "Check sensitive data exposure.",
                "fake-specialist",
                0,
                ""
        );

        specialist.executeTask("review this", context, task, loop, null);

        assertSame(specialist, loop.request.agent());
        assertSame(context, loop.request.context());
        assertTrue(loop.request.taskPrompt().contains("Security check"));
        assertTrue(loop.request.taskPrompt().contains("Check sensitive data exposure."));
    }

    @Test
    void reflectReturnsOkWhenEvaluatorIsSatisfied() {
        FakeSpecialistAgent specialist = new FakeSpecialistAgent(
                context -> ReflectionEvaluator.ReflectionEvaluation.satisfied(0.95)
        );

        ReflectionResult result = specialist.reflect(intent(), task(0), "complete answer", context());

        assertTrue(result.satisfied());
        assertTrue(result.remediationTasks().isEmpty());
    }

    @Test
    void reflectCreatesRemediationTaskWhenEvaluatorFindsGap() {
        FakeSpecialistAgent specialist = new FakeSpecialistAgent(
                context -> ReflectionEvaluator.ReflectionEvaluation.gap(
                        0.4,
                        "Need more evidence.",
                        List.of("Collect more local evidence.")
                )
        );

        ReflectionResult result = specialist.reflect(intent(), task(0), "无法确认", context());

        assertFalse(result.satisfied());
        assertEquals("Need more evidence.", result.gap());
        assertEquals(1, result.remediationTasks().size());
        assertEquals(1, result.remediationTasks().get(0).depth());
        assertEquals("Collect more local evidence.", result.remediationTasks().get(0).description());
    }

    @Test
    void reflectSkipsEvaluationAtMaxDepth() {
        CountingReflectionEvaluator evaluator = new CountingReflectionEvaluator();
        FakeSpecialistAgent specialist = new FakeSpecialistAgent(evaluator);

        ReflectionResult result = specialist.reflect(intent(), task(2), "", context());

        assertTrue(result.satisfied());
        assertEquals(0, evaluator.calls);
    }

    private static ConversationContext context() {
        return new ConversationContext("s1", "p1", "", "main", "java", "diff", "", List.of(), List.of(), List.of(), List.of());
    }

    private static IntentAnalysisResult intent() {
        return new IntentAnalysisResult(
                IntentType.SECURITY_ANALYSIS,
                List.of(IntentType.SECURITY_ANALYSIS),
                35,
                true,
                true,
                false,
                "test"
        );
    }

    private static SubTask task(int depth) {
        return SubTask.of(
                "task-1",
                IntentType.SECURITY_ANALYSIS,
                "Security check",
                "Check sensitive data exposure.",
                "fake-specialist",
                depth,
                ""
        );
    }

    private static class CapturingLoop implements AgentLoop {
        private LoopRequest request;

        @Override
        public String loopType() {
            return "TEST";
        }

        @Override
        public SpecialistExecutionResult execute(LoopRequest request) {
            this.request = request;
            return new SpecialistExecutionResult(List.of(), new SpecialistReport("fake-specialist", "Fake Specialist", "ok"));
        }
    }

    private static class FakeSpecialistAgent extends SpecialistAgent {
        FakeSpecialistAgent() {
            super(new NoOpTextGenerator(), unavailableProvider(), unavailableProvider());
        }

        FakeSpecialistAgent(ReflectionEvaluator reflectionEvaluator) {
            super(new NoOpTextGenerator(), unavailableProvider(), unavailableProvider(), reflectionEvaluator);
        }

        @Override
        public String specialistId() {
            return "fake-specialist";
        }

        @Override
        public String getName() {
            return "Fake Specialist";
        }

        @Override
        public String specialty() {
            return "fake";
        }

        @Override
        public List<IntentType> supportedIntents() {
            return List.of(IntentType.SECURITY_ANALYSIS);
        }

        @Override
        protected String getSpecialistSystemPrompt() {
            return "fake";
        }

        @Override
        protected List<String> preferredToolNames() {
            return List.of();
        }

        @Override
        protected String fallbackAnalysis(String userMessage, ReactContext context, List<ToolCallResult> toolResults) {
            return "fallback";
        }
    }

    private static <T> ObjectProvider<T> unavailableProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }

            @Override
            public T getObject() {
                return null;
            }

            @Override
            public Stream<T> stream() {
                return Stream.empty();
            }
        };
    }

    private static class NoOpTextGenerator implements AgentTextGenerator {
        @Override
        public String generate(String agentName, String instruction, String input, Map<String, Object> context) {
            return "";
        }
    }

    private static class CountingReflectionEvaluator implements ReflectionEvaluator {
        private int calls;

        @Override
        public ReflectionEvaluation evaluate(ReflectionContext context) {
            calls++;
            return ReflectionEvaluation.gap(0.3, "gap", List.of());
        }
    }
}
