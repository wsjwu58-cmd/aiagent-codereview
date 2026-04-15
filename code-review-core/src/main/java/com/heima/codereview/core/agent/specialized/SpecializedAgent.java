package com.heima.codereview.core.agent.specialized;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.BaseAgent;
import com.heima.codereview.core.agent.react.ReactContext;
import com.heima.codereview.core.agent.react.ReactDecision;
import com.heima.codereview.core.agent.react.ReactState;
import com.heima.codereview.core.agent.react.ToolCallResult;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolDefinition;
import com.heima.codereview.tools.mcp.McpToolExecutor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class SpecializedAgent extends BaseAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected final AgentTextGenerator textGenerator;
    private final McpToolExecutor mcpToolExecutor;
    private final McpClient mcpClient;

    protected SpecializedAgent(AgentTextGenerator textGenerator,
                               ObjectProvider<McpToolExecutor> toolExecutorProvider,
                               ObjectProvider<McpClient> mcpClientProvider) {
        this.textGenerator = textGenerator;
        this.mcpToolExecutor = toolExecutorProvider.getIfAvailable();
        this.mcpClient = mcpClientProvider.getIfAvailable();
    }

    @Override
    public String getId() {
        return specialistId();
    }

    public abstract String specialistId();

    protected abstract String getSpecialistSystemPrompt();

    protected abstract List<String> preferredToolNames();

    protected abstract String fallbackAnalysis(String userMessage, ReactContext context, List<ToolCallResult> toolResults);

    public boolean shouldTerminate(ReactState state) {
        if (state == null) {
            return true;
        }
        return state.finalAnswerReady()
                || state.isStuck()
                || (state.toolResults().size() >= maxToolCalls() && state.hasMeaningfulContent());
    }

    protected int maxToolCalls() {
        return 3;
    }

    public List<ToolCallResult> collectToolResults(String userMessage, ReactContext context) {
        return List.of();
    }

    public ReactDecision decideNextAction(String userMessage, ReactContext context, ReactState state) {
        ReactDecision planned = planWithModel(userMessage, context, state);
        if (planned != null) {
            return planned;
        }
        return fallbackDecision(userMessage, context, state);
    }

    public ToolCallResult callTool(String toolName,
                                   Map<String, Object> requestedParams,
                                   String userMessage,
                                   ReactContext context,
                                   ReactState state) {
        Map<String, Object> params = new LinkedHashMap<>(buildDefaultToolParams(toolName, userMessage, context, state));
        if (requestedParams != null) {
            requestedParams.forEach((key, value) -> {
                if (value != null && !(value instanceof String text && text.isBlank())) {
                    params.put(key, value);
                }
            });
        }
        String input = formatParams(params);
        if (mcpToolExecutor == null) {
            return new ToolCallResult(toolName, input, "Tool executor is unavailable in the current runtime.");
        }
        try {
            Object output = mcpToolExecutor.execute(toolName, params);
            return new ToolCallResult(toolName, input, shorten(stringify(output), 2400));
        } catch (Exception e) {
            return new ToolCallResult(toolName, input, "Tool execution failed: " + safe(e.getMessage()));
        }
    }

    public String observeToolResult(ToolCallResult toolResult, ReactContext context, ReactState state) {
        String output = toolResult == null ? "" : toolResult.output();
        if (output.isBlank()) {
            return "Tool " + (toolResult == null ? "" : toolResult.toolName()) + " executed without additional output.";
        }
        return "Observation from " + toolResult.toolName() + ": " + shorten(output, 260);
    }

    public String generateAnalysis(String userMessage, ReactContext context, List<ToolCallResult> toolResults) {
        String input = """
                [User Question]
                %s

                [Repository]
                %s

                [Repository Diff]
                %s

                [Full Code Context]
                %s

                [Recent Messages]
                %s

                [Cross Session Memory]
                %s

                [Related Reviews]
                %s

                [Norms]
                %s

                [Observed Tools]
                %s

                Synthesize the final answer in Simplified Chinese.
                Keep the structure:
                1. Findings
                2. Evidence
                3. Recommended next actions

                When code issues are found, also provide a concrete refactoring suggestion and a refactored code snippet.
                """.formatted(
                safe(userMessage),
                context.conversationContext().repositorySummary(),
                safe(context.conversationContext().repositoryContext()),
                safe(context.conversationContext().fullCodeContext()),
                join(context.conversationContext().recentMessages()),
                join(context.conversationContext().crossSessionMemories()),
                join(context.conversationContext().relatedReviews()),
                join(context.conversationContext().relevantNorms()),
                formatToolResults(toolResults)
        );
        Map<String, Object> generationContext = buildBaseContext(context);
        generationContext.put("disableToolCallbacks", true);
        String generated = textGenerator.generate(
                getName(),
                getSpecialistSystemPrompt(),
                input,
                generationContext
        );
        if (generated != null && !generated.isBlank()) {
            return generated.trim();
        }
        return fallbackAnalysis(userMessage, context, toolResults);
    }

    protected List<McpToolDefinition> availableTools() {
        if (mcpClient == null) {
            return List.of();
        }
        List<String> preferred = preferredToolNames();
        return mcpClient.listTools().stream()
                .filter(tool -> preferred.isEmpty() || preferred.contains(tool.name()))
                .toList();
    }

    protected Map<String, Object> buildDefaultToolParams(String toolName,
                                                         String userMessage,
                                                         ReactContext context,
                                                         ReactState state) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", safe(userMessage));
        params.put("projectId", safe(context.conversationContext().projectId()));
        params.put("sessionId", safe(context.conversationContext().sessionId()));
        params.put("repoUrl", safe(context.conversationContext().repoUrl()));
        params.put("branch", safe(context.conversationContext().branch()));
        params.put("language", safe(context.conversationContext().language()));
        params.put("projectKey", safe(context.conversationContext().projectId()));
        params.put("topK", 4);
        params.put("limit", 4);
        return switch (toolName) {
            case "git_diff_fetch" -> keepOnly(params, "repoUrl", "branch", "language");
            case "review_history_search" -> keepOnly(params, "query", "projectId", "sessionId", "topK");
            case "session_memory_read" -> keepOnly(params, "sessionId");
            case "chat_history_search" -> keepOnly(params, "query", "sessionId", "topK");
            case "norm_search" -> keepOnly(params, "query", "projectId", "limit");
            case "sonar_scan" -> keepOnly(params, "projectKey", "branch");
            default -> params;
        };
    }

    protected int fallbackToolScore(String toolName,
                                    String userMessage,
                                    ReactContext context,
                                    ReactState state) {
        int score = preferredToolNames().contains(toolName) ? 20 : 0;
        String normalized = safe(userMessage).toLowerCase();
        if ("git_diff_fetch".equals(toolName)
                && !state.hasToolCall(toolName)
                && !safe(context.conversationContext().repoUrl()).isBlank()
                && !context.conversationContext().hasRepositoryContext()) {
            score += 100;
        }
        if ("review_history_search".equals(toolName)
                && (!state.hasToolCall(toolName)
                || containsAny(normalized, "history", "review", "previous", "last"))) {
            score += 55;
        }
        if ("session_memory_read".equals(toolName)
                && !state.hasToolCall(toolName)
                && containsAny(normalized, "continue", "previous", "session", "before", "earlier")) {
            score += 65;
        }
        if ("chat_history_search".equals(toolName)
                && !state.hasToolCall(toolName)
                && containsAny(normalized, "history", "earlier", "similar", "before", "past")) {
            score += 60;
        }
        if ("norm_search".equals(toolName)
                && !state.hasToolCall(toolName)
                && (containsAny(normalized, "norm", "standard", "guideline", "pdf", "practice")
                || specialistId().contains("rag"))) {
            score += 70;
        }
        if ("sonar_scan".equals(toolName)
                && !state.hasToolCall(toolName)
                && !safe(context.conversationContext().projectId()).isBlank()
                && (specialistId().contains("security")
                || specialistId().contains("performance")
                || containsAny(normalized, "security", "performance", "vulnerability", "bottleneck"))) {
            score += 50;
        }
        if (state.hasToolCall(toolName, formatParams(buildDefaultToolParams(toolName, userMessage, context, state)))) {
            return Integer.MIN_VALUE;
        }
        return score;
    }

    protected String join(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "N/A";
        }
        return String.join("\n- ", items).indent(2).trim();
    }

    protected String formatToolResults(List<ToolCallResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return "N/A";
        }
        return toolResults.stream()
                .filter(Objects::nonNull)
                .map(item -> item.toolName() + " => " + safe(item.output()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("N/A");
    }

    protected boolean containsAny(String text, String... keywords) {
        String normalized = safe(text).toLowerCase();
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    protected boolean looksLikeCode(String text) {
        return containsAny(text, "{", "}", "class ", "public ", "private ", "function ", "select ", "insert ", "update ");
    }

    protected String safe(String text) {
        return text == null ? "" : text;
    }

    protected String shorten(String text, int maxLength) {
        String normalized = safe(text).replace("\r", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private ReactDecision planWithModel(String userMessage, ReactContext context, ReactState state) {
        if (!textGenerator.available()) {
            return null;
        }
        List<McpToolDefinition> tools = availableTools();
        if (tools.isEmpty()) {
            return ReactDecision.finish("No registered tools are available, finish with the collected evidence.", "", "no_tools");
        }

        Map<String, Object> planningContext = buildBaseContext(context);
        planningContext.put("disableToolCallbacks", true);
        planningContext.put("scene", specialistId() + "-planner");

        String input = """
                You are planning the next ReAct step for a specialist agent.
                Decide whether to call exactly one tool or finish.

                [User Question]
                %s

                [Current Iteration]
                %s

                [Repository]
                %s

                [Observed Tool Results]
                %s

                [Available Tools]
                %s

                Output JSON only:
                {
                  "thought": "...",
                  "action": "TOOL" or "FINISH",
                  "toolName": "...",
                  "params": {},
                  "finalAnswer": "...",
                  "terminationReason": "..."
                }
                """.formatted(
                safe(userMessage),
                state.iteration(),
                context.conversationContext().repositorySummary(),
                formatToolResults(state.toolResults()),
                formatToolDefinitions(tools)
        );

        String raw = textGenerator.generate(
                getName() + "-planner",
                getSpecialistSystemPrompt() + "\nPlan one explicit ReAct step. JSON only. Do not call tools directly.",
                input,
                planningContext
        );
        return parseDecision(raw, tools, userMessage, context, state);
    }

    private ReactDecision parseDecision(String raw,
                                        List<McpToolDefinition> tools,
                                        String userMessage,
                                        ReactContext context,
                                        ReactState state) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(extractJson(raw));
            String action = safe(node.path("action").asText());
            String thought = safe(node.path("thought").asText());
            if ("TOOL".equalsIgnoreCase(action)) {
                String toolName = safe(node.path("toolName").asText());
                if (tools.stream().noneMatch(tool -> tool.name().equals(toolName))) {
                    return null;
                }
                Map<String, Object> params = buildDefaultToolParams(toolName, userMessage, context, state);
                JsonNode paramsNode = node.path("params");
                if (paramsNode.isObject()) {
                    paramsNode.fields().forEachRemaining(entry -> params.put(entry.getKey(), jsonValue(entry.getValue())));
                }
                return ReactDecision.tool(thought, toolName, params);
            }
            if ("FINISH".equalsIgnoreCase(action)) {
                return ReactDecision.finish(
                        thought,
                        safe(node.path("finalAnswer").asText()),
                        safe(node.path("terminationReason").asText())
                );
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ReactDecision fallbackDecision(String userMessage, ReactContext context, ReactState state) {
        List<McpToolDefinition> tools = availableTools();
        McpToolDefinition bestTool = null;
        int bestScore = Integer.MIN_VALUE;
        for (McpToolDefinition tool : tools) {
            int score = fallbackToolScore(tool.name(), userMessage, context, state);
            if (score > bestScore) {
                bestScore = score;
                bestTool = tool;
            }
        }
        if (bestTool == null || bestScore <= 0) {
            return ReactDecision.finish("The current evidence is sufficient for a final synthesis.", "", "no_high_value_tool");
        }
        return ReactDecision.tool(
                "Call " + bestTool.name() + " to gather one more high-value observation.",
                bestTool.name(),
                buildDefaultToolParams(bestTool.name(), userMessage, context, state)
        );
    }

    private Map<String, Object> buildBaseContext(ReactContext context) {
        Map<String, Object> generationContext = new LinkedHashMap<>();
        generationContext.put("sessionId", safe(context.conversationContext().sessionId()));
        generationContext.put("projectId", safe(context.conversationContext().projectId()));
        generationContext.put("repoUrl", safe(context.conversationContext().repoUrl()));
        generationContext.put("branch", safe(context.conversationContext().branch()));
        generationContext.put("language", safe(context.conversationContext().language()));
        generationContext.put("scene", specialistId());
        return generationContext;
    }

    private Map<String, Object> keepOnly(Map<String, Object> source, String... keys) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !(value instanceof String text && text.isBlank())) {
                filtered.put(key, value);
            }
        }
        return filtered;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String formatParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + stringify(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String formatToolDefinitions(List<McpToolDefinition> tools) {
        return tools.stream()
                .map(tool -> "- " + tool.name() + ": " + tool.description() + " | schema=" + stringify(tool.inputSchema()))
                .collect(Collectors.joining("\n"));
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), jsonValue(entry.getValue())));
            return values;
        }
        return node.asText();
    }
}
