package com.heima.codereview.core.chain.nodes;

import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.chain.Node;
import com.heima.codereview.core.chain.NodeResult;
import com.heima.codereview.core.chain.ReviewContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(3)
public class PerformanceNode implements Node {

    @Override
    public String getName() {
        return "性能分析";
    }

    @Override
    public NodeResult execute(ReviewContext context) {
        List<ReviewIssue> issues = new ArrayList<>();
        String code = context.codeContent() == null ? "" : context.codeContent().toLowerCase();
        if (code.contains("for (") && code.split("for \\(").length > 3) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "MEDIUM",
                    "unknown",
                    1,
                    "循环层级较深，可能影响性能",
                    "PERF_DEEP_LOOP",
                    "考虑提前剪枝或使用索引结构降低复杂度"
            ));
        }
        if (code.contains("select *") || code.contains("findall(")) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "MEDIUM",
                    "repository",
                    1,
                    "可能存在全量查询，建议分页或限定字段",
                    "PERF_FULL_SCAN",
                    "增加分页参数并只查询必要字段"
            ));
        }
        return new NodeResult(getName(), issues);
    }
}
