package com.heima.codereview.api.ai;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class SpringAiAlibabaAgentTextGenerator implements AgentTextGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAlibabaAgentTextGenerator.class);
    private static final int LOG_PREVIEW_MAX = 160;
    private static final int LOG_OUTPUT_MAX = 4000;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final McpToolExecutor mcpToolExecutor;

    public SpringAiAlibabaAgentTextGenerator(ObjectProvider<ChatModel> chatModelProvider,
                                             McpToolExecutor mcpToolExecutor) {
        this.chatModelProvider = chatModelProvider;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    @Override
    public String generate(String agentName, String instruction, String input, Map<String, Object> context) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        boolean disableToolCallbacks = isToolCallbackDisabled(safeContext);
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("AI dialog degraded because no ChatModel is available. agentName={}, instructionLength={}, inputLength={}, inputPreview={}",
                    agentName, safeLength(instruction), safeLength(input), preview(input));
            return "";
        }

        log.info("AI dialog started. agentName={}, instructionLength={}, inputLength={}, inputPreview={}",
                agentName, safeLength(instruction), safeLength(input), preview(input));
        try {
            String systemPrompt = "Current agent: " + agentName + "\n" + defaultString(instruction)
                    + (disableToolCallbacks
                    ? "\nTool callbacks are disabled for this request. Reason only with the provided context."
                    : "\nIf the task needs external context, history, or repository code, proactively call the available tools.")
                    + "\nAlways answer in Simplified Chinese unless the instruction explicitly requires another language.";
            String output = ChatClient.create(chatModel)
                    .prompt()
                    .system(systemPrompt)
                    .user(input)
                    .toolCallbacks(disableToolCallbacks ? List.of() : buildToolCallbacks(safeContext))
                    .toolContext(safeContext)
                    .call()
                    .content();
            if (output == null || output.isBlank()) {
                log.warn("AI dialog returned empty content. agentName={}, costMs={}", agentName, System.currentTimeMillis() - start);
                return "";
            }
            log.info("AI dialog finished. agentName={}, costMs={}, outputLength={}, outputPreview={}",
                    agentName, System.currentTimeMillis() - start, output.length(), preview(output));
            log.info("AI dialog output. agentName={}, output={}", agentName, outputForLog(output));
            return output;
        } catch (Exception e) {
            log.error("AI dialog failed. agentName={}, costMs={}, reason={}",
                    agentName, System.currentTimeMillis() - start, e.getMessage(), e);
            return "";
        }
    }

    @Override
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null;
    }

    private static int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= LOG_PREVIEW_MAX) {
            return normalized;
        }
        return normalized.substring(0, LOG_PREVIEW_MAX) + "...";
    }

    private static String outputForLog(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r", " ").replace("\n", "\\n").trim();
        if (normalized.length() <= LOG_OUTPUT_MAX) {
            return normalized;
        }
        return normalized.substring(0, LOG_OUTPUT_MAX) + "...(truncated)";
    }

    private List<ToolCallback> buildToolCallbacks(Map<String, Object> context) {
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.add(FunctionToolCallback.builder("review_history_search", (ReviewHistoryToolRequest request, ToolContext toolContext) ->
                        mcpToolExecutor.execute("review_history_search", Map.of(
                                "query", defaultString(request.query()),
                                "projectId", stringFromContext(toolContext, "projectId"),
                                "sessionId", stringFromContext(toolContext, "sessionId"),
                                "topK", request.topK() == null ? 5 : request.topK()
                        )))
                .description("Search related review history for the current project or session.")
                .inputType(ReviewHistoryToolRequest.class)
                .build());
        callbacks.add(FunctionToolCallback.builder("session_memory_read", (SessionMemoryToolRequest request, ToolContext toolContext) ->
                        mcpToolExecutor.execute("session_memory_read", Map.of(
                                "sessionId", stringFromContext(toolContext, "sessionId")
                        )))
                .description("Read recent chat memory for the current session.")
                .inputType(SessionMemoryToolRequest.class)
                .build());
        callbacks.add(FunctionToolCallback.builder("chat_history_search", (ChatHistoryToolRequest request, ToolContext toolContext) ->
                        mcpToolExecutor.execute("chat_history_search", Map.of(
                                "query", defaultString(request.query()),
                                "sessionId", stringFromContext(toolContext, "sessionId"),
                                "topK", request.topK() == null ? 5 : request.topK()
                        )))
                .description("Search semantically related chat history outside the current turn.")
                .inputType(ChatHistoryToolRequest.class)
                .build());
        callbacks.add(FunctionToolCallback.builder("norm_search", (NormSearchToolRequest request, ToolContext toolContext) ->
                        mcpToolExecutor.execute("norm_search", Map.of(
                                "query", defaultString(request.query()),
                                "projectId", firstNonBlank(request.projectId(), stringFromContext(toolContext, "projectId")),
                                "limit", request.limit() == null ? 5 : request.limit()
                        )))
                .description("Search uploaded PDF norms or project standards.")
                .inputType(NormSearchToolRequest.class)
                .build());
        callbacks.add(FunctionToolCallback.builder("code_refactor", (CodeRefactorToolRequest request, ToolContext toolContext) ->
                        mcpToolExecutor.execute("code_refactor", Map.of(
                                "originalCode", defaultString(request.originalCode()),
                                "suggestions", request.suggestions() == null ? List.of() : request.suggestions()
                        )))
                .description("Generate refactoring output from code and suggestions.")
                .inputType(CodeRefactorToolRequest.class)
                .build());
        if (hasNonBlankContext(context, "projectId")) {
            callbacks.add(FunctionToolCallback.builder("sonar_scan", (SonarScanToolRequest request, ToolContext toolContext) ->
                            mcpToolExecutor.execute("sonar_scan", Map.of(
                                    "projectKey", firstNonBlank(request.projectKey(), stringFromContext(toolContext, "projectId")),
                                    "branch", firstNonBlank(request.branch(), stringFromContext(toolContext, "branch"))
                            )))
                    .description("Run a Sonar-style scan for the current project and branch.")
                    .inputType(SonarScanToolRequest.class)
                    .build());
        }
        if (hasNonBlankContext(context, "repoUrl")) {
            callbacks.add(FunctionToolCallback.builder("git_diff_fetch", (GitDiffToolRequest request, ToolContext toolContext) ->
                            mcpToolExecutor.execute("git_diff_fetch", Map.of(
                                    "repoUrl", firstNonBlank(request.repoUrl(), stringFromContext(toolContext, "repoUrl")),
                                    "branch", firstNonBlank(request.branch(), stringFromContext(toolContext, "branch")),
                                    "language", firstNonBlank(request.language(), stringFromContext(toolContext, "language"))
                            )))
                    .description("Fetch repository diff using repoUrl, branch, and optional language from context or tool input.")
                    .inputType(GitDiffToolRequest.class)
                    .build());
        }
        return callbacks;
    }

    private static String stringFromContext(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return "";
        }
        Object value = toolContext.getContext().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return defaultString(second);
    }

    private static String defaultString(String text) {
        return text == null ? "" : text;
    }

    private static boolean hasNonBlankContext(Map<String, Object> context, String key) {
        Object value = context.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private static boolean isToolCallbackDisabled(Map<String, Object> context) {
        Object value = context.get("disableToolCallbacks");
        return value instanceof Boolean disabled && disabled;
    }

    private record GitDiffToolRequest(String repoUrl, String branch, String language) {
    }

    private record CodeRefactorToolRequest(String originalCode, List<String> suggestions) {
    }

    private record ReviewHistoryToolRequest(String query, String projectId, String sessionId, Integer topK) {
    }

    private record SessionMemoryToolRequest(String sessionId) {
    }

    private record ChatHistoryToolRequest(String query, Integer topK) {
    }

    private record NormSearchToolRequest(String query, String projectId, Integer limit) {
    }

    private record SonarScanToolRequest(String projectKey, String branch) {
    }
}
