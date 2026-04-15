package com.heima.codereview.api.config;

import com.heima.codereview.core.memory.ChatMemory;
import com.heima.codereview.rag.ChatHistoryRepository;
import com.heima.codereview.rag.PdfRepository;
import com.heima.codereview.rag.knowledge.ReviewKnowledgeBase;
import com.heima.codereview.tools.git.GitDiffFetcher;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolDefinition;
import com.heima.codereview.tools.refactor.CodeRefactorer;
import com.heima.codereview.tools.sonar.SonarClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class ToolRegistrationConfig {

    private final McpClient mcpClient;
    private final GitDiffFetcher gitDiffFetcher;
    private final SonarClient sonarClient;
    private final CodeRefactorer codeRefactorer;
    private final ReviewKnowledgeBase reviewKnowledgeBase;
    private final ChatMemory chatMemory;
    private final PdfRepository pdfRepository;
    private final ChatHistoryRepository chatHistoryRepository;

    public ToolRegistrationConfig(McpClient mcpClient,
                                  GitDiffFetcher gitDiffFetcher,
                                  SonarClient sonarClient,
                                  CodeRefactorer codeRefactorer,
                                  ReviewKnowledgeBase reviewKnowledgeBase,
                                  ChatMemory chatMemory,
                                  PdfRepository pdfRepository,
                                  ChatHistoryRepository chatHistoryRepository) {
        this.mcpClient = mcpClient;
        this.gitDiffFetcher = gitDiffFetcher;
        this.sonarClient = sonarClient;
        this.codeRefactorer = codeRefactorer;
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.chatMemory = chatMemory;
        this.pdfRepository = pdfRepository;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @PostConstruct
    public void registerTools() {
        mcpClient.registerTool(definition(
                "git_diff_fetch",
                "Fetch the latest repository diff or commit window for the current repo.",
                Map.of("repoUrl", "string", "branch", "string", "language", "string")), params -> gitDiffFetcher.fetchDiff(
                asString(params.get("repoUrl")),
                asString(params.get("branch")),
                asString(params.get("language"))
        ));

        mcpClient.registerTool(definition(
                "sonar_scan",
                "Run a Sonar-style scan for the current project and branch.",
                Map.of("projectKey", "string", "branch", "string")), params -> sonarClient.scan(
                asString(params.get("projectKey")),
                asString(params.get("branch"))
        ));

        mcpClient.registerTool(definition(
                "code_refactor",
                "Generate a refactored code snippet from source code and review suggestions.",
                Map.of("originalCode", "string", "suggestions", "array<string>")), params -> codeRefactorer.refactor(
                asString(params.get("originalCode")),
                asStringList(params.get("suggestions"))
        ));

        mcpClient.registerTool(definition(
                "review_history_search",
                "Search related review history for the current project or session.",
                Map.of("query", "string", "projectId", "string", "sessionId", "string", "topK", "integer")), params -> reviewKnowledgeBase.search(
                        asString(params.get("query")),
                        asString(params.get("projectId")),
                        asString(params.get("sessionId")),
                        asInt(params.get("topK"), 5))
                .stream()
                .map(item -> "reviewId=" + item.reviewId() + ", content=" + item.content())
                .collect(Collectors.joining("\n")));

        mcpClient.registerTool(definition(
                "session_memory_read",
                "Read recent chat memory from the current session.",
                Map.of("sessionId", "string")), params -> chatMemory.recent(asString(params.get("sessionId")))
                .stream()
                .map(item -> item.role() + ": " + item.content())
                .collect(Collectors.joining("\n")));

        mcpClient.registerTool(definition(
                "chat_history_search",
                "Search semantically related chat history outside the current turn.",
                Map.of("query", "string", "sessionId", "string", "topK", "integer")), params -> chatHistoryRepository.searchRelevant(
                        asString(params.get("query")),
                        asString(params.get("sessionId")),
                        asInt(params.get("topK"), 5))
                .stream()
                .map(item -> "[" + item.sessionId() + "] " + item.role() + ": " + item.content())
                .collect(Collectors.joining("\n")));

        mcpClient.registerTool(definition(
                "norm_search",
                "Search uploaded PDF norms or project standards.",
                Map.of("query", "string", "projectId", "string", "limit", "integer")), params -> pdfRepository.searchNorms(
                        asString(params.get("query")),
                        asString(params.get("projectId")),
                        asInt(params.get("limit"), 5))
                .stream()
                .map(item -> item.fileName() + " page " + item.pageNumber() + ": " + item.summary())
                .collect(Collectors.joining("\n")));
    }

    private McpToolDefinition definition(String name, String description, Map<String, Object> inputSchema) {
        return new McpToolDefinition(name, description, new LinkedHashMap<>(inputSchema));
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }
}
