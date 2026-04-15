package com.heima.codereview.core.agent;

import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.core.chain.ReviewContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SummarizerAgent extends BaseAgent {

    private final AgentTextGenerator textGenerator;

    public SummarizerAgent(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    @Override
    public String getName() {
        return "汇总Agent";
    }

    public String execute(ReviewContext reviewContext, ReviewReport report, String advisorOutput, String refactoredCode) {
        String fallback = "审查完成。总问题数: " + report.getTotalIssues()
                + "，评分: " + report.getScore()
                + "。\n重点建议:\n" + advisorOutput
                + "\n\n重构代码预览长度: " + refactoredCode.length() + " 字符。"
                + "\n历史上下文条数: " + reviewContext.historicalReviews().size()
                + "，会话记忆条数: " + reviewContext.recentMessages().size();
        String aiInput = "请将下面审查结果汇总为结构化中文报告：\n" + fallback;
        String aiOutput = textGenerator.generate(
                getName(),
                "你是审查汇总专家，请输出简洁报告，并说明这次结果与上下文历史的关系。",
                aiInput,
                Map.of(
                        "sessionId", reviewContext.sessionId(),
                        "reviewId", reviewContext.reviewId(),
                        "projectId", reviewContext.projectId() == null ? "" : reviewContext.projectId(),
                        "repoUrl", reviewContext.repoUrl() == null ? "" : reviewContext.repoUrl(),
                        "branch", reviewContext.branch() == null ? "" : reviewContext.branch(),
                        "scene", "review_summary"
                )
        );
        if (aiOutput == null || aiOutput.isBlank() || aiOutput.startsWith("[本地降级模式]")) {
            return fallback;
        }
        return aiOutput;
    }
}
