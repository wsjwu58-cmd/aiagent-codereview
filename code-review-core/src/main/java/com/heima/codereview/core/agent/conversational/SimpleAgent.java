package com.heima.codereview.core.agent.conversational;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SimpleAgent extends BaseAgent implements ConversationalAgent {

    private static final String SYSTEM_PROMPT = """
            You are the fast conversational agent in a code review assistant.
            Use the user question, repository context, recent session history, related reviews, and norms to answer.
            Always answer in Simplified Chinese.
            If repoUrl exists but repository diff is empty, proactively call git_diff_fetch.
            If context is insufficient, clearly say what is missing instead of inventing code or execution results.
            """;

    private final AgentTextGenerator textGenerator;

    public SimpleAgent(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    @Override
    public String getName() {
        return "快速回答 Agent";
    }

    @Override
    public String respond(String userMessage, ConversationContext context) {
        String input = """
                [Project ID]
                %s

                [Session ID]
                %s

                [Repository]
                %s

                [User Question]
                %s

                [Repository Diff]
                %s

                [Recent Messages]
                %s

                [Cross Session Memory]
                %s

                [Related Reviews]
                %s

                [Norms]
                %s
                """.formatted(
                safe(context.projectId()),
                safe(context.sessionId()),
                context.repositorySummary(),
                safe(userMessage),
                safe(context.repositoryContext()),
                join(context.recentMessages()),
                join(context.crossSessionMemories()),
                join(context.relatedReviews()),
                join(context.relevantNorms())
        );

        String answer = textGenerator.generate(
                getName(),
                SYSTEM_PROMPT,
                input,
                Map.of(
                        "sessionId", safe(context.sessionId()),
                        "projectId", safe(context.projectId()),
                        "repoUrl", safe(context.repoUrl()),
                        "branch", safe(context.branch()),
                        "language", safe(context.language()),
                        "scene", "simple-chat"
                )
        );
        if (answer != null && !answer.isBlank()) {
            return answer.trim();
        }
        return "当前上下文不足以给出稳定结论，请补充更具体的代码、报错信息或仓库范围，我会继续沿着现有上下文分析。";
    }

    private String join(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "N/A";
        }
        return "- " + String.join("\n- ", items);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
