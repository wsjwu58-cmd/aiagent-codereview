package com.heima.codereview.core.agent;

import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import com.heima.codereview.core.chain.ReviewContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AdvisorAgent extends BaseAgent {

    private final AgentTextGenerator textGenerator;

    public AdvisorAgent(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    @Override
    public String getName() {
        return "顾问Agent";
    }

    public String execute(ReviewContext reviewContext, ReviewReport report) {
        List<ReviewSuggestion> suggestions = new ArrayList<>();
        int priority = 1;
        for (ReviewIssue issue : report.getIssues()) {
            suggestions.add(new ReviewSuggestion(priority++, "处理规则: " + issue.ruleId(), issue.suggestion()));
        }

        if (suggestions.isEmpty()) {
            suggestions.add(new ReviewSuggestion(1, "保持当前质量", "当前代码没有明显问题，建议补充边界条件测试。"));
        }

        suggestions = suggestions.stream()
                .sorted(Comparator.comparingInt(ReviewSuggestion::priority))
                .limit(8)
                .collect(Collectors.toList());
        report.setSuggestions(suggestions);

        String fallbackText = suggestions.stream()
                .map(item -> item.priority() + ". " + item.title() + " - " + item.description())
                .collect(Collectors.joining("\n"));

        String historyContext = reviewContext.historicalReviews().isEmpty()
                ? "无历史审查记录"
                : reviewContext.historicalReviews().stream()
                .limit(3)
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));
        String messageContext = reviewContext.recentMessages().isEmpty()
                ? "无会话记忆"
                : reviewContext.recentMessages().stream()
                .limit(5)
                .map(item -> item.role() + ": " + item.content())
                .collect(Collectors.joining("\n"));

        String aiInput = "请根据以下审查建议给出中文优化计划，分优先级输出：\n"
                + "【当前问题】\n" + fallbackText
                + "\n【会话记忆】\n" + messageContext
                + "\n【历史审查】\n" + historyContext;
        String aiOutput = textGenerator.generate(
                getName(),
                "你是资深代码顾问，请将建议整理为可执行清单，并结合会话记忆与历史审查结果避免重复建议。",
                aiInput,
                Map.of(
                        "sessionId", reviewContext.sessionId(),
                        "reviewId", reviewContext.reviewId(),
                        "projectId", reviewContext.projectId() == null ? "" : reviewContext.projectId(),
                        "repoUrl", reviewContext.repoUrl() == null ? "" : reviewContext.repoUrl(),
                        "branch", reviewContext.branch() == null ? "" : reviewContext.branch(),
                        "scene", "review_advisor"
                )
        );
        if (aiOutput == null || aiOutput.isBlank() || aiOutput.startsWith("[本地降级模式]")) {
            return fallbackText;
        }
        return aiOutput;
    }
}
