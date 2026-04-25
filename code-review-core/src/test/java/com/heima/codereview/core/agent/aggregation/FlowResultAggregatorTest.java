package com.heima.codereview.core.agent.aggregation;

import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowResultAggregatorTest {

    private final FlowResultAggregator aggregator = new FlowResultAggregator(new NoOpTextGenerator());

    @Test
    void aggregateReviewResultsFallsBackToStructuredMarkdownWhenModelUnavailable() {
        ReviewReport report = new ReviewReport();
        report.setScore(86);
        report.setTotalIssues(2);
        report.setHighCount(1);
        report.setLowCount(1);

        String output = aggregator.aggregateReviewResults(
                new IntentAnalysisResult(IntentType.CODE_REVIEW, List.of(IntentType.CODE_REVIEW), 35, true, true, false, "test"),
                context(),
                report,
                "建议拆分方法",
                "class Demo {}",
                List.of(),
                List.of(),
                "整体风险可控"
        );

        assertTrue(output.contains("## 综合结论"));
        assertTrue(output.contains("- 评分: 86"));
        assertTrue(output.contains("建议拆分方法"));
        assertTrue(output.contains("class Demo {}"));
    }

    private static ConversationContext context() {
        return new ConversationContext("s1", "p1", "", "main", "java", "diff", "", List.of(), List.of(), List.of(), List.of());
    }

    private static class NoOpTextGenerator implements AgentTextGenerator {
        @Override
        public String generate(String agentName, String instruction, String input, Map<String, Object> context) {
            return "";
        }
    }
}
