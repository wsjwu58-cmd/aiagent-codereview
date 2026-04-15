package com.heima.codereview.core.agent.specialized;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.react.ReactContext;
import com.heima.codereview.core.agent.react.ReactState;
import com.heima.codereview.core.agent.react.ToolCallResult;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewSpecialistAgent extends SpecializedAgent {

    public ReviewSpecialistAgent(AgentTextGenerator textGenerator,
                                 ObjectProvider<McpToolExecutor> toolExecutorProvider,
                                 ObjectProvider<McpClient> mcpClientProvider) {
        super(textGenerator, toolExecutorProvider, mcpClientProvider);
    }

    @Override
    public String specialistId() {
        return "review-specialist";
    }

    @Override
    public String getName() {
        return "Review Specialist";
    }

    @Override
    protected String getSpecialistSystemPrompt() {
        return "You are a code review specialist. Focus on correctness, maintainability, regressions, and code quality. Always answer in Simplified Chinese.";
    }

    @Override
    protected List<String> preferredToolNames() {
        return List.of("git_diff_fetch", "review_history_search", "session_memory_read", "chat_history_search", "norm_search");
    }

    @Override
    public boolean shouldTerminate(ReactState state) {
        return super.shouldTerminate(state);
    }

    @Override
    protected String fallbackAnalysis(String userMessage, ReactContext context, List<ToolCallResult> toolResults) {
        StringBuilder builder = new StringBuilder();
        builder.append("发现或判断:\n");
        if (context.conversationContext().hasRepositoryContext()) {
            builder.append("- 已获取仓库差异，可围绕最近改动开展审查。\n");
        } else if (looksLikeCode(userMessage)) {
            builder.append("- 当前更像是代码片段审查，应重点关注结构、边界和可维护性。\n");
        } else {
            builder.append("- 当前缺少足够源码上下文，结论以审查思路为主。\n");
        }
        builder.append("依据:\n");
        builder.append("- 已观察 ").append(toolResults.size()).append(" 条工具结果。\n");
        builder.append("建议动作:\n");
        builder.append("- 优先补充目标模块、异常栈或仓库差异范围，再继续逐项审查。");
        return builder.toString();
    }
}
