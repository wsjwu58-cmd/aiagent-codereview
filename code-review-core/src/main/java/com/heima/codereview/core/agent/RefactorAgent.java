package com.heima.codereview.core.agent;

import com.heima.codereview.core.chain.ReviewContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RefactorAgent extends BaseAgent {

    private final AgentTextGenerator textGenerator;

    public RefactorAgent(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    @Override
    public String getName() {
        return "重构Agent";
    }

    public String execute(ReviewContext reviewContext, String advisorSuggestion) {
        String base = reviewContext.codeContent() == null ? "" : reviewContext.codeContent();
        if (base.isBlank()) {
            return "// 未提供可重构的代码片段";
        }

        String historyContext = reviewContext.historicalReviews().isEmpty()
                ? "无历史审查记录"
                : reviewContext.historicalReviews().stream()
                .limit(2)
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));
        String aiInput = "请根据建议重构下方代码，仅返回重构后的代码。\n【建议】\n"
                + advisorSuggestion + "\n【历史审查】\n" + historyContext + "\n【代码】\n" + base;
        String aiOutput = textGenerator.generate(
                getName(),
                "你是重构专家，请输出可直接替换的代码，并参考历史审查中反复出现的问题。",
                aiInput,
                Map.of(
                        "sessionId", reviewContext.sessionId(),
                        "reviewId", reviewContext.reviewId(),
                        "projectId", reviewContext.projectId() == null ? "" : reviewContext.projectId(),
                        "repoUrl", reviewContext.repoUrl() == null ? "" : reviewContext.repoUrl(),
                        "branch", reviewContext.branch() == null ? "" : reviewContext.branch(),
                        "scene", "review_refactor"
                )
        );
        if (aiOutput != null && !aiOutput.isBlank() && !aiOutput.startsWith("[本地降级模式]")) {
            return aiOutput;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("// 以下是基于审查建议生成的示例重构版本\n");
        builder.append("// 建议摘要: ").append(advisorSuggestion.split("\\n")[0]).append("\n");
        builder.append(base.replace("System.out.println", "log.info"));
        return builder.toString();
    }
}
