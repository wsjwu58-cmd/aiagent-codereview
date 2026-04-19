package com.heima.codereview.core.agent.specialized;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.SpecialistAgent;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.react.ReactContext;
import com.heima.codereview.core.agent.react.ReactState;
import com.heima.codereview.core.agent.react.ToolCallResult;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagSpecialistAgent extends SpecialistAgent {

    public RagSpecialistAgent(AgentTextGenerator textGenerator,
                              ObjectProvider<McpToolExecutor> toolExecutorProvider,
                              ObjectProvider<McpClient> mcpClientProvider) {
        super(textGenerator, toolExecutorProvider, mcpClientProvider);
    }

    @Override
    public String specialistId() {
        return "rag-specialist";
    }

    @Override
    public String getName() {
        return "RAG Specialist";
    }

    @Override
    public String specialty() {
        return "knowledge";
    }

    @Override
    public List<IntentType> supportedIntents() {
        return List.of(IntentType.KNOWLEDGE_RETRIEVAL, IntentType.DOCUMENTATION_GENERATION);
    }

    @Override
    protected String getSpecialistSystemPrompt() {
        return "You are a knowledge and standards specialist. Prioritize project norms, historical reviews, and long-term memory when giving evidence-based conclusions. Always answer in Simplified Chinese.";
    }

    @Override
    protected List<String> preferredToolNames() {
        return List.of("norm_search", "review_history_search", "chat_history_search", "session_memory_read", "git_diff_fetch");
    }

    @Override
    protected int maxToolCalls() {
        return 4;
    }

    @Override
    public boolean shouldTerminate(ReactState state) {
        return super.shouldTerminate(state);
    }

    @Override
    protected String fallbackAnalysis(String userMessage, ReactContext context, List<ToolCallResult> toolResults) {
        StringBuilder builder = new StringBuilder();
        builder.append("发现或判断:\n");
        if (context.conversationContext().relevantNorms().isEmpty()) {
            builder.append("- 当前没有直接命中的规范文档，回答将以历史记录和通用最佳实践为主。\n");
        } else {
            builder.append("- 已命中相关规范文档，可据此补充更具体的审查标准。\n");
        }
        builder.append("依据:\n");
        builder.append("- 已整合规范、跨会话记忆与历史审查结果。\n");
        builder.append("建议动作:\n");
        builder.append("- 如需更强约束，请继续上传项目专属规范 PDF。\n");
        return builder.toString();
    }
}
