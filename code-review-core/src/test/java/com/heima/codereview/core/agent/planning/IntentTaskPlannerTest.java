package com.heima.codereview.core.agent.planning;

import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentTaskPlannerTest {

    private final IntentTaskPlanner planner = new IntentTaskPlanner();

    @Test
    void forceReviewUsesCodeReviewAsPrimaryIntent() {
        IntentAnalysisResult result = planner.analyzeIntent("随便看看", emptyContext(), true);

        assertEquals(IntentType.CODE_REVIEW, result.primaryIntent());
        assertTrue(result.requiresRepositoryContext());
    }

    @Test
    void buildInitialTasksIncludesParallelizableCandidateIntents() {
        IntentAnalysisResult intent = planner.analyzeIntent(
                "review this diff for security and performance",
                contextWithRepository(),
                false
        );

        List<SubTask> tasks = planner.buildInitialTasks(intent);

        assertEquals(List.of(
                        IntentType.CODE_REVIEW,
                        IntentType.SECURITY_ANALYSIS,
                        IntentType.PERFORMANCE_ANALYSIS
                ),
                tasks.stream().map(SubTask::intentType).toList());
    }

    @Test
    void decomposeTaskAddsKnowledgeRetrievalWhenNormsAreAvailable() {
        ConversationContext context = new ConversationContext(
                "s1",
                "p1",
                "",
                "",
                "java",
                "diff",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of("安全规范")
        );
        IntentAnalysisResult intent = planner.analyzeIntent("按规范检查代码", context, false);

        List<SubTask> tasks = planner.decomposeTask(intent, context);

        assertTrue(tasks.stream().anyMatch(task -> task.intentType() == IntentType.KNOWLEDGE_RETRIEVAL));
    }

    private static ConversationContext emptyContext() {
        return new ConversationContext("", "", "", "", "", "", "", List.of(), List.of(), List.of(), List.of());
    }

    private static ConversationContext contextWithRepository() {
        return new ConversationContext("s1", "p1", "", "main", "java", "diff", "", List.of(), List.of(), List.of(), List.of());
    }
}
