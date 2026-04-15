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
@Order(4)
public class StyleCheckNode implements Node {

    @Override
    public String getName() {
        return "风格检查";
    }

    @Override
    public NodeResult execute(ReviewContext context) {
        List<ReviewIssue> issues = new ArrayList<>();
        String code = context.codeContent() == null ? "" : context.codeContent();
        String[] lines = code.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > 140) {
                issues.add(new ReviewIssue(
                        IdUtils.uuid(),
                        "LOW",
                        "unknown",
                        i + 1,
                        "单行代码过长，可读性较差",
                        "STYLE_LONG_LINE",
                        "将长语句拆分并优化命名"
                ));
                break;
            }
        }
        if (code.contains("\t")) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "LOW",
                    "unknown",
                    1,
                    "检测到Tab缩进，建议统一空格风格",
                    "STYLE_TAB_INDENT",
                    "建议统一为4个空格缩进"
            ));
        }
        return new NodeResult(getName(), issues);
    }
}
