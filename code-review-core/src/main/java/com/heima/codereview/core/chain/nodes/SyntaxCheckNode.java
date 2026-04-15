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
@Order(1)
public class SyntaxCheckNode implements Node {

    @Override
    public String getName() {
        return "语法检查";
    }

    @Override
    public NodeResult execute(ReviewContext context) {
        List<ReviewIssue> issues = new ArrayList<>();
        String code = context.codeContent() == null ? "" : context.codeContent();
        int braces = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') {
                braces++;
            } else if (c == '}') {
                braces--;
            }
        }
        if (braces != 0) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "HIGH",
                    "unknown",
                    1,
                    "花括号可能不匹配，建议检查代码块闭合关系",
                    "SYNTAX_BRACE_MISMATCH",
                    "请检查if/for/method等结构体的大括号配对"
            ));
        }

        if (code.contains("System.out.println(") && "java".equalsIgnoreCase(context.language())) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "LOW",
                    "Main.java",
                    1,
                    "检测到调试输出语句，建议使用日志框架",
                    "JAVA_DEBUG_PRINT",
                    "将System.out.println替换为slf4j日志"
            ));
        }

        return new NodeResult(getName(), issues);
    }
}
