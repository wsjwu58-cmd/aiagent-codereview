package com.heima.codereview.core.agent;

import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.core.chain.Node;
import com.heima.codereview.core.chain.NodeResult;
import com.heima.codereview.core.chain.ReviewChain;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Component
public class ReviewAgent extends BaseAgent {

    private final ReviewChain reviewChain;

    public ReviewAgent(ReviewChain reviewChain) {
        this.reviewChain = reviewChain;
    }

    @Override
    public String getName() {
        return "审查Agent";
    }

    public ReviewReport execute(AgentExecutionContext context, BiConsumer<Integer, Node> progressConsumer) {
        List<NodeResult> nodeResults = reviewChain.process(context.reviewContext(), progressConsumer);
        List<ReviewIssue> allIssues = new ArrayList<>();
        nodeResults.forEach(item -> allIssues.addAll(item.getIssues()));

        ReviewReport report = new ReviewReport();
        report.setReviewId(context.reviewContext().reviewId());
        report.setIssues(allIssues);
        report.setTotalIssues(allIssues.size());
        report.setCriticalCount((int) allIssues.stream().filter(i -> "CRITICAL".equals(i.severity())).count());
        report.setHighCount((int) allIssues.stream().filter(i -> "HIGH".equals(i.severity())).count());
        report.setMediumCount((int) allIssues.stream().filter(i -> "MEDIUM".equals(i.severity())).count());
        report.setLowCount((int) allIssues.stream().filter(i -> "LOW".equals(i.severity())).count());
        report.setScore(ReviewChain.scoreFromIssues(allIssues));
        int historySize = context.reviewContext().historicalReviews().size();
        int memorySize = context.reviewContext().recentMessages().size();
        if (allIssues.isEmpty()) {
            report.setSummary("未发现明显问题，建议继续结合单元测试和压测进行验证。"
                    + " 本次审查参考了 " + historySize + " 条历史记录与 " + memorySize + " 条会话记忆。");
        } else {
            report.setSummary("共发现 " + allIssues.size() + " 个问题，请优先处理高危与严重项。"
                    + " 本次审查参考了 " + historySize + " 条历史记录与 " + memorySize + " 条会话记忆。");
        }

        return report;
    }
}
