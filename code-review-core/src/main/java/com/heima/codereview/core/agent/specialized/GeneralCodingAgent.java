package com.heima.codereview.core.agent.specialized;

import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.SpecialistAgent;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.planning.SubTask;
import com.heima.codereview.core.agent.react.ReactContext;
import com.heima.codereview.core.agent.react.ToolCallResult;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GeneralCodingAgent extends SpecialistAgent {

    public GeneralCodingAgent(AgentTextGenerator textGenerator,
                              ObjectProvider<McpToolExecutor> toolExecutorProvider,
                              ObjectProvider<McpClient> mcpClientProvider) {
        super(textGenerator, toolExecutorProvider, mcpClientProvider);
    }

    @Override
    public String specialistId() {
        return "general-coding-agent";
    }

    @Override
    public String getName() {
        return "General Coding Agent";
    }

    @Override
    public String specialty() {
        return "general-coding";
    }

    @Override
    public List<IntentType> supportedIntents() {
        return List.of(IntentType.GENERAL_CODING, IntentType.SIMPLE_ANSWER);
    }

    @Override
    protected String getSpecialistSystemPrompt() {
        return "You are a general coding agent. Answer programming questions, explain code, debug issues, and recommend practical next steps in Simplified Chinese.";
    }

    @Override
    protected List<String> preferredToolNames() {
        return List.of(
                "code_search",
                "file_operation",
                "local_file_list",
                "local_file_read",
                "local_file_search",
                "local_file_write",
                "local_file_delete",
                "web_search",
                "norm_search",
                "chat_history_search",
                "git_diff_fetch"
        );
    }

    @Override
    public List<SubTask> proposeSubTasks(SubTask task, String result, ConversationContext context) {
        String normalized = result == null ? "" : result.toLowerCase(Locale.ROOT);
        List<SubTask> proposals = new ArrayList<>();
        if (containsAny(normalized, "architecture", "架构", "模块边界")) {
            proposals.add(task.nextDepth(
                    IdUtils.withPrefix("task"),
                    IntentType.ARCHITECTURE_ANALYSIS,
                    "Need architecture view",
                    "The coding answer surfaced architecture-level concerns that need a dedicated architecture pass.",
                    "architecture-specialist"
            ));
        }
        if (containsAny(normalized, "规范", "标准", "best practice", "guideline")) {
            proposals.add(task.nextDepth(
                    IdUtils.withPrefix("task"),
                    IntentType.KNOWLEDGE_RETRIEVAL,
                    "Retrieve coding norms",
                    "The answer depends on project norms or external standards and should retrieve supporting guidance.",
                    "rag-specialist"
            ));
        }
        return proposals;
    }

    @Override
    protected String fallbackAnalysis(String userMessage, ReactContext context, List<ToolCallResult> toolResults) {
        StringBuilder builder = new StringBuilder();
        builder.append("问题判断:\n");
        builder.append("- 当前更像是通用编程问题，需要结合仓库上下文、已有历史和必要的搜索结果给出建议。\n");
        builder.append("证据:\n");
        builder.append("- 已观察 ").append(toolResults.size()).append(" 条工具结果。\n");
        builder.append("建议动作:\n");
        builder.append("- 可以继续补充报错堆栈、相关代码片段或期望行为，我会沿着同一思路继续收敛。");
        return builder.toString();
    }
}
