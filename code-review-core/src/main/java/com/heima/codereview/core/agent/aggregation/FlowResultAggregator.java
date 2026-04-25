package com.heima.codereview.core.agent.aggregation;

import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.SpecialistReport;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.TaskExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class FlowResultAggregator {

    private static final String AGENT_NAME = "Planner Flow Agent";

    private final AgentTextGenerator textGenerator;

    public FlowResultAggregator(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    public String aggregatePlanningResults(IntentAnalysisResult intent,
                                           ConversationContext context,
                                           List<TaskExecutionResult> taskResults,
                                           List<SpecialistReport> reports,
                                           String primarySummary) {
        String reportText = formatReports(reports);
        String taskText = formatTaskStatus(taskResults);
        if (textGenerator.available()) {
            String input = """
                    [Primary Intent]
                    %s

                    [Planner Rationale]
                    %s

                    [Repository]
                    %s

                    [Primary Summary]
                    %s

                    [Specialist Reports]
                    %s

                    [Task Status]
                    %s

                    Merge these planning results into one final answer in Simplified Chinese.
                    Use the structure:
                    1. 结论摘要
                    2. 核心依据
                    3. 下一步建议
                    """.formatted(
                    intent.primaryIntent(),
                    intent.rationale(),
                    context.repositorySummary(),
                    safe(primarySummary),
                    reportText.isBlank() ? "N/A" : reportText,
                    taskText.isBlank() ? "N/A" : taskText
            );
            String output = textGenerator.generate(
                    AGENT_NAME,
                    "You are the planner agent. Aggregate specialist results into one reliable final answer in Simplified Chinese.",
                    input,
                    generationContext(context)
            );
            if (output != null && !output.isBlank()) {
                return output.trim();
            }
        }
        return fallbackPlanningAggregate(primarySummary, reportText, taskText);
    }

    public String aggregateReviewResults(IntentAnalysisResult intent,
                                         ConversationContext context,
                                         ReviewReport reviewReport,
                                         String advisorOutput,
                                         String refactoredCode,
                                         List<TaskExecutionResult> taskResults,
                                         List<SpecialistReport> reports,
                                         String primarySummary) {
        String metricsText = formatMetrics(reviewReport);
        String issueText = formatIssues(reviewReport, 5);
        String suggestionText = formatSuggestions(reviewReport, 5);
        String refactorText = hasText(advisorOutput) ? advisorOutput.trim() : suggestionText;
        String refactoredCodePreview = formatRefactoredCode(refactoredCode, context.language());
        String reportText = formatReports(reports);
        String taskText = formatTaskStatus(taskResults);

        if (textGenerator.available()) {
            String input = """
                    [Primary Intent]
                    %s

                    [Planner Rationale]
                    %s

                    [Repository]
                    %s

                    [Primary Summary]
                    %s

                    [Review Metrics]
                    %s

                    [Top Issues]
                    %s

                    [Refactoring Advice]
                    %s

                    [Refactored Code Preview]
                    %s

                    [Specialist Reports]
                    %s

                    [Task Status]
                    %s

                    Merge these planning results into one complete review report in Simplified Chinese.
                    Use strict Markdown and include all of the following sections:
                    ## 综合结论
                    ## 评分与风险概览
                    ## 关键问题
                    ## 代码重构建议
                    ## 重构代码预览
                    ## 协同分析补充
                    ## 下一步建议

                    Requirements:
                    - Explicitly mention score and issue counts.
                    - Summarize the most important issues with file, severity, and suggestion when available.
                    - Include refactoring advice.
                    - If refactored code is available, include a fenced code block preview.
                    - If no extra specialist output exists, say so briefly instead of omitting the section.
                    """.formatted(
                    intent.primaryIntent(),
                    intent.rationale(),
                    context.repositorySummary(),
                    safe(primarySummary),
                    metricsText.isBlank() ? "N/A" : metricsText,
                    issueText.isBlank() ? "N/A" : issueText,
                    refactorText.isBlank() ? "N/A" : refactorText,
                    refactoredCodePreview.isBlank() ? "N/A" : refactoredCodePreview,
                    reportText.isBlank() ? "N/A" : reportText,
                    taskText.isBlank() ? "N/A" : taskText
            );
            String output = textGenerator.generate(
                    AGENT_NAME,
                    "You are the planner agent. Aggregate specialist results into one reliable and complete review report in Simplified Chinese.",
                    input,
                    generationContext(context)
            );
            if (output != null && !output.isBlank()) {
                return output.trim();
            }
        }

        return fallbackReviewAggregate(primarySummary, metricsText, issueText, refactorText, refactoredCodePreview, reportText, taskText);
    }

    private Map<String, Object> generationContext(ConversationContext context) {
        return Map.of(
                "sessionId", safe(context.sessionId()),
                "projectId", safe(context.projectId()),
                "repoUrl", safe(context.repoUrl()),
                "branch", safe(context.branch()),
                "language", safe(context.language()),
                "scene", "planner-aggregate",
                "disableToolCallbacks", true
        );
    }

    private String formatReports(List<SpecialistReport> reports) {
        return reports.stream()
                .filter(Objects::nonNull)
                .map(item -> "[" + item.agentName() + "]\n" + safe(item.summary()))
                .collect(Collectors.joining("\n\n"));
    }

    private String formatTaskStatus(List<TaskExecutionResult> taskResults) {
        return taskResults.stream()
                .map(item -> "- " + item.task().title() + " => " + (item.success() ? "SUCCESS" : "FAILED")
                        + (item.failureReason().isBlank() ? "" : " (" + item.failureReason() + ")"))
                .collect(Collectors.joining("\n"));
    }

    private String fallbackPlanningAggregate(String primarySummary, String reportText, String taskText) {
        StringBuilder builder = new StringBuilder();
        builder.append("结论摘要:\n");
        builder.append(safe(primarySummary).isBlank() ? "- 已完成规划执行，但缺少可聚合的主结果。\n" : safe(primarySummary) + "\n");
        builder.append("\n核心依据:\n");
        builder.append(reportText == null || reportText.isBlank() ? "- 暂无额外 specialist 结果。\n" : reportText + "\n");
        builder.append("\n下一步建议:\n");
        builder.append(taskText == null || taskText.isBlank() ? "- 如需更深入分析，请补充更多仓库上下文或代码片段。" : taskText);
        return builder.toString();
    }

    private String fallbackReviewAggregate(String primarySummary,
                                           String metricsText,
                                           String issueText,
                                           String refactorText,
                                           String refactoredCodePreview,
                                           String reportText,
                                           String taskText) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 综合结论\n");
        builder.append(hasText(primarySummary) ? primarySummary.trim() : "已完成审查流程，但缺少可聚合的主结论。");
        builder.append("\n\n## 评分与风险概览\n");
        builder.append(hasText(metricsText) ? metricsText : "- 暂无评分与问题统计。");
        builder.append("\n\n## 关键问题\n");
        builder.append(hasText(issueText) ? issueText : "- 暂无可列出的关键问题。");
        builder.append("\n\n## 代码重构建议\n");
        builder.append(hasText(refactorText) ? refactorText : "- 暂无额外重构建议。");
        builder.append("\n\n## 重构代码预览\n");
        builder.append(hasText(refactoredCodePreview) ? refactoredCodePreview : "```text\n暂无重构代码预览\n```");
        builder.append("\n\n## 协同分析补充\n");
        builder.append(hasText(reportText) ? reportText : "- 暂无额外 specialist 补充结论。");
        builder.append("\n\n## 下一步建议\n");
        builder.append(hasText(taskText) ? taskText : "- 如需更深入分析，请补充更多仓库上下文或代码片段。");
        return builder.toString();
    }

    private String formatMetrics(ReviewReport reviewReport) {
        if (reviewReport == null) {
            return "";
        }
        return """
                - 评分: %s
                - 问题总数: %s
                - 严重问题: %s
                - 高风险问题: %s
                - 中风险问题: %s
                - 低风险问题: %s
                """.formatted(
                reviewReport.getScore(),
                reviewReport.getTotalIssues(),
                reviewReport.getCriticalCount(),
                reviewReport.getHighCount(),
                reviewReport.getMediumCount(),
                reviewReport.getLowCount()
        ).trim();
    }

    private String formatIssues(ReviewReport reviewReport, int limit) {
        if (reviewReport == null || reviewReport.getIssues() == null || reviewReport.getIssues().isEmpty()) {
            return "";
        }
        return reviewReport.getIssues().stream()
                .limit(Math.max(limit, 1))
                .map(this::formatIssue)
                .filter(this::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatIssue(ReviewIssue issue) {
        if (issue == null) {
            return "";
        }
        String location = hasText(issue.file()) ? issue.file() + (issue.lineNumber() > 0 ? ":" + issue.lineNumber() : "") : "未知位置";
        return """
                ### %s | %s
                - 位置: %s
                - 问题: %s
                - 建议: %s
                """.formatted(
                safe(issue.severity()),
                safe(issue.ruleId()),
                location,
                safe(issue.message()),
                safe(issue.suggestion())
        ).trim();
    }

    private String formatSuggestions(ReviewReport reviewReport, int limit) {
        if (reviewReport == null || reviewReport.getSuggestions() == null || reviewReport.getSuggestions().isEmpty()) {
            return "";
        }
        return reviewReport.getSuggestions().stream()
                .limit(Math.max(limit, 1))
                .map(this::formatSuggestion)
                .collect(Collectors.joining("\n"));
    }

    private String formatSuggestion(ReviewSuggestion suggestion) {
        if (suggestion == null) {
            return "";
        }
        return "- P" + suggestion.priority() + " " + safe(suggestion.title()) + ": " + safe(suggestion.description());
    }

    private String formatRefactoredCode(String refactoredCode, String language) {
        if (!hasText(refactoredCode)) {
            return "";
        }
        String normalizedLanguage = safe(language).trim().toLowerCase(Locale.ROOT);
        String preview = shortenText(refactoredCode.replace("\r\n", "\n"), 2200);
        return "```" + normalizedLanguage + "\n" + preview + "\n```";
    }

    private String shortenText(String text, int maxLength) {
        String normalized = safe(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n// ... truncated";
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
