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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SpecializedAgent extends BaseAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> CONTEXT_BOUND_PARAM_KEYS = List.of("sessionId", "projectId", "repoUrl", "branch", "language");
    private static final Pattern WINDOWS_PATH_PATTERN =
            Pattern.compile("([A-Za-z]:[\\\\/][^\\s\"',;]+(?:[\\\\/][^\\s\"',;]+)*)");
    private static final Pattern UNIX_PATH_PATTERN =
            Pattern.compile("((?:/[^\\s\"',;]+)+)");
    private static final List<String> DEFAULT_LOCAL_FILE_FILTERS = List.of(
            "*.java",
            "*.kt",
            "*.xml",
            "*.yml",
            "*.yaml",
            "*.properties",
            "*.sql",
            "*.js",
            "*.jsx",
            "*.ts",
            "*.tsx",
            "*.vue",
            "*.py",
            "*.go",
            "*.md"
    );
    private static final List<String> LOW_YIELD_RETRIEVAL_TOOLS = List.of(
            "review_history_search",
            "session_memory_read",
            "chat_history_search",
            "norm_search",
            "web_search",
            "git_diff_fetch",
            "file_operation",
            "code_search"
    );
    private static final int MAX_PLANNING_TOOL_RESULTS = 4;
    private static final int MAX_PLANNING_TOOL_RESULT_LENGTH = 320;
    private static final int MAX_TOOL_QUERY_LENGTH = 320;
    private static final int MAX_RETRIEVAL_QUERY_LENGTH = 240;
    private static final int MAX_TOOL_RESULT_WINDOW = 5;

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
        Map<String, Object> params = mergeToolParams(
                toolName,
                buildDefaultToolParams(toolName, userMessage, context, state),
                requestedParams
        );
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

                Synthesize the final answer in Simplified Chinese using strict Markdown.
                Formatting rules:
                - Every heading, bullet, numbered item, and bold label must start on its own line.
                - Leave a blank line between headings, paragraphs, and lists.
                - Never place markdown markers such as ##, ###, -, 1., or **label**: in the middle of an existing paragraph.
                Keep the structure:
                ## Findings
                ### 1. ...
                - 问题描述: ...
                - 证据: ...

                ## Evidence
                - ...

                ## Recommended Next Actions
                - ...

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
        String localPath = extractCandidatePath(userMessage);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", safe(userMessage));
        params.put("path", localPath);
        params.put("action", "list");
        params.put("keyword", safe(userMessage));
        params.put("pattern", extractSearchPattern(userMessage, localPath));
        params.put("filters", DEFAULT_LOCAL_FILE_FILTERS);
        params.put("content", "");
        params.put("projectId", safe(context.conversationContext().projectId()));
        params.put("sessionId", safe(context.conversationContext().sessionId()));
        params.put("repoUrl", safe(context.conversationContext().repoUrl()));
        params.put("branch", safe(context.conversationContext().branch()));
        params.put("language", safe(context.conversationContext().language()));
        params.put("topK", 4);
        params.put("limit", 4);
        return filterToolParams(toolName, params);
    }

    private Map<String, Object> filterToolParams(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "git_diff_fetch" -> keepOnly(params, "repoUrl", "branch", "language");
            case "review_history_search" -> keepOnly(params, "query", "projectId", "sessionId", "topK");
            case "session_memory_read" -> keepOnly(params, "sessionId");
            case "chat_history_search" -> keepOnly(params, "query", "sessionId", "topK");
            case "norm_search" -> keepOnly(params, "query", "projectId", "limit");
            case "file_operation" -> keepOnly(params, "repoUrl", "path", "action", "keyword", "limit");
            case "local_file_list" -> keepOnly(params, "path", "filters");
            case "local_file_read" -> keepOnly(params, "path");
            case "local_file_write" -> keepOnly(params, "path", "content");
            case "local_file_search" -> keepOnly(params, "path", "pattern", "filters");
            case "local_file_delete" -> keepOnly(params, "path");
            case "code_search" -> keepOnly(params, "query", "repoUrl", "language", "limit");
            case "web_search" -> keepOnly(params, "query", "limit");
            default -> params;
        };
    }

    protected int fallbackToolScore(String toolName,
                                    String userMessage,
                                    ReactContext context,
                                    ReactState state) {
        if (isLowYieldRetrievalTool(toolName) && hasAnyLowYieldRetrievalCall(state)) {
            return Integer.MIN_VALUE;
        }

        int score = preferredToolNames().contains(toolName) ? 20 : 0;
        String normalized = safe(userMessage).toLowerCase();
        String localPath = extractCandidatePath(userMessage);
        String localSearchPattern = extractSearchPattern(userMessage, localPath);
        boolean hasLocalPath = !localPath.isBlank();
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
        if ("file_operation".equals(toolName)
                && !state.hasToolCall(toolName)
                && !safe(context.conversationContext().repoUrl()).isBlank()
                && containsAny(normalized, "file", "path", "目录", "文件", "read", "查看", "列出")) {
            score += 65;
        }
        if ("local_file_list".equals(toolName)
                && !state.hasToolCall(toolName)
                && hasLocalPath
                && containsAny(normalized, "folder", "directory", "dir", "list", "scan", "tree", "local", "目录", "列出", "扫描")) {
            score += 80;
        }
        if ("local_file_read".equals(toolName)
                && !state.hasToolCall(toolName)
                && hasLocalPath
                && (looksLikeFilePath(localPath)
                || containsAny(normalized, "read", "open", "show", "view", "cat", "查看", "读取", "打开", "内容"))) {
            score += 90;
        }
        if ("local_file_search".equals(toolName)
                && !state.hasToolCall(toolName)
                && hasLocalPath
                && !localSearchPattern.isBlank()
                && containsAny(normalized, "search", "find", "grep", "keyword", "pattern", "lookup", "搜索", "查找", "匹配")) {
            score += 85;
        }
        if ("local_file_write".equals(toolName)
                && !state.hasToolCall(toolName)
                && hasLocalPath
                && containsAny(normalized, "write", "overwrite", "update file", "modify file", "create file", "写入", "覆盖", "修改文件", "创建文件")) {
            score += 55;
        }
        if ("local_file_delete".equals(toolName)
                && !state.hasToolCall(toolName)
                && hasLocalPath
                && containsAny(normalized, "delete", "remove file", "unlink", "删除", "移除")) {
            score += 45;
        }
        if ("code_search".equals(toolName)
                && !state.hasToolCall(toolName)
                && !safe(context.conversationContext().repoUrl()).isBlank()
                && containsAny(normalized, "class", "method", "search", "where", "调用", "实现", "查找")) {
            score += 70;
        }
        if ("web_search".equals(toolName)
                && !state.hasToolCall(toolName)
                && containsAny(normalized, "official", "docs", "document", "stackoverflow", "官网", "文档", "资料")) {
            score += 45;
        }
        if (state.hasToolCall(toolName, formatParams(buildDefaultToolParams(toolName, userMessage, context, state)))) {
            return Integer.MIN_VALUE;
        }
        if (isLowYieldRetrievalTool(toolName) && state.toolCallCount(toolName) > 0) {
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

    private String formatPlanningToolResults(List<ToolCallResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return "N/A";
        }
        int start = Math.max(0, toolResults.size() - MAX_PLANNING_TOOL_RESULTS);
        return toolResults.subList(start, toolResults.size()).stream()
                .filter(Objects::nonNull)
                .map(item -> "- " + item.toolName() + " => "
                        + shorten(safe(item.output()).replace("\n", " "), MAX_PLANNING_TOOL_RESULT_LENGTH))
                .collect(Collectors.joining("\n"));
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

                Planning rules (STRICT - must follow):
                1. PRIORITY: For code review tasks, you MUST first call norm_search to retrieve project standards before analyzing code.
                2. EXISTING DATA: Check [Observed Tool Results] first. If git_diff_fetch already returned code/diff, do NOT call it again.
                3. NO REPEAT: You MUST NOT call any tool that was already called in previous iterations.
                4. ONE TOOL: Each tool can only be called ONCE per session.
                5. REUSE: If you need data that was already retrieved, use the existing results instead of re-fetching.
                6. FINISH if: The provided code or current evidence is already enough to analyze the issue.
                7. Never override sessionId, projectId, repoUrl, branch, or language from the current context.

                Tool selection priority for review tasks:
                - norm_search (MUST be called first for review tasks)
                - review_history_search (optional, for historical patterns)
                - Then analysis tools if norms are already retrieved

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
                formatPlanningToolResults(state.toolResults()),
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
                if (shouldStopAfterRepeatedRetrieval(toolName, state)) {
                    return ReactDecision.finish(thought, "", "repeated_low_yield_retrieval");
                }
                Map<String, Object> requestedParams = new LinkedHashMap<>();
                JsonNode paramsNode = node.path("params");
                if (paramsNode.isObject()) {
                    paramsNode.fields().forEachRemaining(entry -> requestedParams.put(entry.getKey(), jsonValue(entry.getValue())));
                }
                Map<String, Object> params = mergeToolParams(
                        toolName,
                        buildDefaultToolParams(toolName, userMessage, context, state),
                        requestedParams
                );
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

    private Map<String, Object> mergeToolParams(String toolName,
                                                Map<String, Object> baseParams,
                                                Map<String, Object> requestedParams) {
        Map<String, Object> params = new LinkedHashMap<>(baseParams);
        if (requestedParams != null) {
            requestedParams.forEach((key, value) -> {
                Object normalized = normalizeToolParam(toolName, key, value);
                if (normalized != null) {
                    params.put(key, normalized);
                }
            });
        }
        return filterToolParams(toolName, params);
    }

    private Object normalizeToolParam(String toolName, String key, Object value) {
        if (key == null || value == null || isContextBoundParam(key)) {
            return null;
        }
        if ("topK".equals(key) || "limit".equals(key)) {
            return clampPositiveNumber(value, MAX_TOOL_RESULT_WINDOW);
        }
        if ("filters".equals(key)) {
            return normalizeStringList(value, 8);
        }
        if (value instanceof String text) {
            if ("content".equals(key)) {
                String normalizedContent = text.replace("\r\n", "\n").replace('\r', '\n');
                return normalizedContent.isBlank() ? null : normalizedContent;
            }
            String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
            if (normalized.isBlank()) {
                return null;
            }
            if ("query".equals(key) || "keyword".equals(key)) {
                int maxLength = isLowYieldRetrievalTool(toolName) ? MAX_RETRIEVAL_QUERY_LENGTH : MAX_TOOL_QUERY_LENGTH;
                return shorten(normalized, maxLength);
            }
            return normalized;
        }
        return value;
    }

    private Integer clampPositiveNumber(Object value, int maxValue) {
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
            return Math.max(1, Math.min(parsed, maxValue));
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> normalizeStringList(Object value, int maxItems) {
        List<?> rawValues;
        if (value instanceof List<?> list) {
            rawValues = list;
        } else {
            rawValues = List.of(value);
        }
        List<String> normalized = new ArrayList<>();
        for (Object rawValue : rawValues) {
            if (rawValue == null) {
                continue;
            }
            String item = String.valueOf(rawValue).trim();
            if (!item.isBlank()) {
                normalized.add(item);
            }
            if (normalized.size() >= maxItems) {
                break;
            }
        }
        return normalized;
    }

    private boolean shouldStopAfterRepeatedRetrieval(String toolName, ReactState state) {
        return isLowYieldRetrievalTool(toolName) && hasAnyLowYieldRetrievalCall(state);
    }

    private boolean hasAnyLowYieldRetrievalCall(ReactState state) {
        if (state == null || state.toolResults() == null) {
            return false;
        }
        return state.toolResults().stream()
                .filter(Objects::nonNull)
                .map(ToolCallResult::toolName)
                .anyMatch(this::isLowYieldRetrievalTool);
    }

    private boolean isLowYieldRetrievalTool(String toolName) {
        return LOW_YIELD_RETRIEVAL_TOOLS.contains(toolName);
    }

    private boolean isContextBoundParam(String key) {
        return CONTEXT_BOUND_PARAM_KEYS.contains(key);
    }

    private String extractCandidatePath(String userMessage) {
        String text = safe(userMessage);
        Matcher windowsMatcher = WINDOWS_PATH_PATTERN.matcher(text);
        if (windowsMatcher.find()) {
            return windowsMatcher.group(1);
        }
        Matcher unixMatcher = UNIX_PATH_PATTERN.matcher(text);
        if (unixMatcher.find()) {
            return unixMatcher.group(1);
        }
        return "";
    }

    private String extractSearchPattern(String userMessage, String extractedPath) {
        String normalized = safe(userMessage);
        if (!safe(extractedPath).isBlank()) {
            normalized = normalized.replace(extractedPath, " ");
        }
        normalized = normalized
                .replaceAll("(?i)please", " ")
                .replaceAll("(?i)local", " ")
                .replaceAll("(?i)folder", " ")
                .replaceAll("(?i)directory", " ")
                .replaceAll("(?i)file", " ")
                .replaceAll("(?i)path", " ")
                .replaceAll("(?i)search", " ")
                .replaceAll("(?i)find", " ")
                .replaceAll("(?i)grep", " ")
                .replaceAll("(?i)list", " ")
                .replaceAll("(?i)read", " ")
                .replaceAll("(?i)open", " ")
                .replaceAll("(?i)view", " ")
                .replace("本地", " ")
                .replace("目录", " ")
                .replace("文件夹", " ")
                .replace("文件", " ")
                .replace("路径", " ")
                .replace("搜索", " ")
                .replace("查找", " ")
                .replace("读取", " ")
                .replace("打开", " ")
                .replace("查看", " ")
                .replace("列出", " ")
                .replace("扫描", " ")
                .replace("，", " ")
                .replace("。", " ")
                .replace("；", " ")
                .replace("：", " ")
                .replace("（", " ")
                .replace("）", " ")
                .replaceAll("[,.;:()\\[\\]{}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 80) {
            return "";
        }
        return normalized;
    }

    private boolean looksLikeFilePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        int slashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dotIndex = path.lastIndexOf('.');
        return dotIndex > slashIndex;
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
