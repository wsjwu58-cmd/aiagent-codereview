package com.heima.codereview.core.agent;

import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.ReactStreamListener;
import com.heima.codereview.core.agent.conversational.SpecialistExecutionResult;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.planning.ReflectionResult;
import com.heima.codereview.core.agent.planning.SubTask;
import com.heima.codereview.core.agent.react.ReactLoop;
import com.heima.codereview.core.agent.specialized.SpecializedAgent;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Locale;

public abstract class SpecialistAgent extends SpecializedAgent implements SelfReflection {

    protected SpecialistAgent(AgentTextGenerator textGenerator,
                              ObjectProvider<McpToolExecutor> toolExecutorProvider,
                              ObjectProvider<McpClient> mcpClientProvider) {
        super(textGenerator, toolExecutorProvider, mcpClientProvider);
    }

    public abstract String specialty();

    public abstract List<IntentType> supportedIntents();

    public boolean canHandle(IntentAnalysisResult intent) {
        if (intent == null) {
            return false;
        }
        if (supportedIntents().contains(intent.primaryIntent())) {
            return true;
        }
        return intent.candidateIntents().stream().anyMatch(supportedIntents()::contains);
    }

    public boolean canHandle(SubTask task) {
        return task != null && supportedIntents().contains(task.intentType());
    }

    public SpecialistExecutionResult executeTask(String userMessage,
                                                 ConversationContext context,
                                                 SubTask task,
                                                 ReactLoop reactLoop,
                                                 ReactStreamListener listener) {
        return reactLoop.execute(this, buildTaskPrompt(userMessage, task), context, listener);
    }

    public List<SubTask> proposeSubTasks(SubTask task, String result, ConversationContext context) {
        return List.of();
    }

    @Override
    public ReflectionResult reflect(IntentAnalysisResult originalIntent,
                                    SubTask task,
                                    String executionResult,
                                    ConversationContext context) {
        if (!requiresFollowUp(executionResult) || task == null || task.depth() >= 2) {
            return ReflectionResult.ok();
        }
        SubTask remediation = task.nextDepth(
                IdUtils.withPrefix("task"),
                task.intentType(),
                task.title() + " follow-up",
                "Close the remaining gap for: " + task.description(),
                specialistId()
        );
        return ReflectionResult.gap("The current answer is incomplete or lacks evidence.", List.of(remediation));
    }

    protected String buildTaskPrompt(String userMessage, SubTask task) {
        return """
                [Original Request]
                %s

                [Assigned Sub Task]
                title=%s
                intent=%s
                description=%s
                depth=%s
                """.formatted(
                userMessage == null ? "" : userMessage,
                task == null ? "" : task.title(),
                task == null ? "" : task.intentType(),
                task == null ? "" : task.description(),
                task == null ? 0 : task.depth()
        );
    }

    protected boolean requiresFollowUp(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.contains("insufficient")
                || normalized.contains("缺少")
                || normalized.contains("无法")
                || normalized.contains("unavailable")
                || normalized.contains("需要更多");
    }
}
