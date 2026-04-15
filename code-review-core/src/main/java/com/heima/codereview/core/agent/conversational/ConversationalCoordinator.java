package com.heima.codereview.core.agent.conversational;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.BaseAgent;
import com.heima.codereview.core.agent.specialized.SpecializedAgent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ConversationalCoordinator extends BaseAgent {

    private final Map<String, SpecializedAgent> specialists;
    private final AgentTextGenerator textGenerator;

    public ConversationalCoordinator(List<SpecializedAgent> specialists, AgentTextGenerator textGenerator) {
        this.specialists = new LinkedHashMap<>();
        for (SpecializedAgent specialist : specialists) {
            this.specialists.put(specialist.getId(), specialist);
        }
        this.textGenerator = textGenerator;
    }

    @Override
    public String getName() {
        return "协调者 Agent";
    }

    public CoordinationDecision analyze(String userMessage, ConversationContext context) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> requiredAgents = new LinkedHashSet<>();

        if (containsAny(normalized, "安全", "漏洞", "注入", "xss", "sql", "token", "secret")) {
            requiredAgents.add("security-specialist");
        }
        if (containsAny(normalized, "性能", "慢", "优化", "复杂度", "查询", "吞吐")) {
            requiredAgents.add("performance-specialist");
        }
        if (!context.relevantNorms().isEmpty() || containsAny(normalized, "规范", "pdf", "标准", "依据", "文档")) {
            requiredAgents.add("rag-specialist");
        }
        if (containsAny(normalized, "审查", "review", "代码", "bug", "重构", "建议") || requiredAgents.isEmpty()) {
            requiredAgents.add("review-specialist");
        }

        if (shouldUseSimpleMode(normalized, context)) {
            return new CoordinationDecision(
                    CoordinationType.SIMPLE_ANSWER,
                    List.of(),
                    false,
                    "问题复杂度较低，优先走快速回答路径。"
            );
        }

        CoordinationType type = requiredAgents.size() > 1
                ? CoordinationType.MULTI_AGENT_COLLABORATION
                : CoordinationType.SINGLE_AGENT;
        return new CoordinationDecision(
                type,
                new ArrayList<>(requiredAgents),
                requiredAgents.contains("rag-specialist"),
                "已根据问题意图拆分为 %s 个专业分析方向。".formatted(requiredAgents.size())
        );
    }

    public List<SpecializedAgent> resolveSpecialists(CoordinationDecision decision) {
        List<SpecializedAgent> resolved = new ArrayList<>();
        for (String requiredAgent : decision.requiredAgents()) {
            SpecializedAgent specialist = specialists.get(requiredAgent);
            if (specialist != null) {
                resolved.add(specialist);
            }
        }
        return resolved;
    }

    public String summarize(String userMessage,
                            ConversationContext context,
                            CoordinationDecision decision,
                            List<SpecialistReport> reports) {
        String reportText = reports.stream()
                .map(item -> "[" + item.agentName() + "]\n" + item.summary())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("N/A");
        String input = """
                [Coordination Decision]
                %s

                [User Question]
                %s

                [Repository]
                %s

                [Repository Diff]
                %s

                [Specialist Reports]
                %s

                [Norms]
                %s

                Merge the specialist results into one final response in Simplified Chinese.
                Keep the structure:
                1. 结论摘要
                2. 核心依据
                3. 下一步建议
                """.formatted(
                decision.rationale(),
                userMessage == null ? "" : userMessage,
                context.repositorySummary(),
                context.repositoryContext() == null || context.repositoryContext().isBlank() ? "N/A" : context.repositoryContext(),
                reportText,
                context.relevantNorms().isEmpty() ? "N/A" : String.join("\n", context.relevantNorms())
        );
        String answer = textGenerator.generate(
                getName(),
                "You are the coordinator agent. Synthesize multiple specialist conclusions into one reliable answer. Always answer in Simplified Chinese.",
                input,
                Map.of(
                        "sessionId", context.sessionId(),
                        "projectId", context.projectId(),
                        "repoUrl", context.repoUrl() == null ? "" : context.repoUrl(),
                        "branch", context.branch() == null ? "" : context.branch(),
                        "language", context.language() == null ? "" : context.language(),
                        "scene", "react-summary"
                )
        );
        if (answer != null && !answer.isBlank()) {
            return answer.trim();
        }
        return "结论摘要：" + decision.rationale() + "\n\n核心依据：\n" + reportText + "\n\n下一步建议：优先处理高风险问题，并补充更具体的仓库范围或代码上下文做二次审查。";
    }

    private boolean shouldUseSimpleMode(String normalized, ConversationContext context) {
        boolean explicitAnalysis = containsAny(normalized, "深度", "详细", "分析", "审查", "review", "检查");
        boolean hasRichContext = !context.relatedReviews().isEmpty() || !context.relevantNorms().isEmpty() || context.hasRepositoryContext();
        return !explicitAnalysis && !hasRichContext && normalized.length() < 18;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
