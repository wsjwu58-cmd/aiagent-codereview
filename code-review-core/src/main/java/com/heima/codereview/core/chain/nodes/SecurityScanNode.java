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
@Order(2)
public class SecurityScanNode implements Node {

    @Override
    public String getName() {
        return "安全扫描";
    }

    @Override
    public NodeResult execute(ReviewContext context) {
        List<ReviewIssue> issues = new ArrayList<>();
        String code = context.codeContent() == null ? "" : context.codeContent().toLowerCase();
        if (code.contains("password=") || code.contains("apikey") || code.contains("secret")) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "CRITICAL",
                    "config",
                    1,
                    "疑似硬编码密钥/密码，请立即移除敏感信息",
                    "SEC_HARDCODED_SECRET",
                    "使用环境变量或密钥管理服务替代硬编码"
            ));
        }
        if (code.contains("runtime.getruntime().exec") || code.contains("eval(")) {
            issues.add(new ReviewIssue(
                    IdUtils.uuid(),
                    "HIGH",
                    "unknown",
                    1,
                    "检测到高风险动态执行逻辑，存在注入风险",
                    "SEC_COMMAND_INJECTION",
                    "增加白名单校验并避免直接执行外部输入"
            ));
        }
        return new NodeResult(getName(), issues);
    }
}
