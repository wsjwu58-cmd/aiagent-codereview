package com.heima.codereview.api.config;

import com.heima.codereview.common.model.norm.NormRecord;
import com.heima.codereview.core.memory.ChatMemory;
import com.heima.codereview.rag.ChatHistoryRepository;
import com.heima.codereview.rag.PdfRepository;
import com.heima.codereview.rag.knowledge.ReviewKnowledgeBase;
import com.heima.codereview.tools.file.FileOperationTool;
import com.heima.codereview.tools.file.LocalFileOperationTool;
import com.heima.codereview.tools.git.GitDiffFetcher;
import com.heima.codereview.tools.mcp.McpClient;
import com.heima.codereview.tools.mcp.McpToolDefinition;
import com.heima.codereview.tools.refactor.CodeRefactorer;
import com.heima.codereview.tools.search.CodeSearchTool;
import com.heima.codereview.tools.search.WebSearchTool;
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
    private final CodeRefactorer codeRefactorer;
    private final ReviewKnowledgeBase reviewKnowledgeBase;
    private final ChatMemory chatMemory;
    private final PdfRepository pdfRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final FileOperationTool fileOperationTool;
    private final LocalFileOperationTool localFileOperationTool;
    private final CodeSearchTool codeSearchTool;
    private final WebSearchTool webSearchTool;

    public ToolRegistrationConfig(McpClient mcpClient,
                                  GitDiffFetcher gitDiffFetcher,
                                  CodeRefactorer codeRefactorer,
                                  ReviewKnowledgeBase reviewKnowledgeBase,
                                  ChatMemory chatMemory,
                                  PdfRepository pdfRepository,
                                  ChatHistoryRepository chatHistoryRepository,
                                  FileOperationTool fileOperationTool,
                                  LocalFileOperationTool localFileOperationTool,
                                  CodeSearchTool codeSearchTool,
                                  WebSearchTool webSearchTool) {
        this.mcpClient = mcpClient;
        this.gitDiffFetcher = gitDiffFetcher;
        this.codeRefactorer = codeRefactorer;
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.chatMemory = chatMemory;
        this.pdfRepository = pdfRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.fileOperationTool = fileOperationTool;
        this.localFileOperationTool = localFileOperationTool;
        this.codeSearchTool = codeSearchTool;
        this.webSearchTool = webSearchTool;
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
                Map.of("query", "string", "projectId", "string", "limit", "integer")), params -> formatNormSearchResult(
                        asString(params.get("query")),
                        asString(params.get("projectId")),
                        asInt(params.get("limit"), 5)));

        mcpClient.registerTool(definition(
                "file_operation",
                "List or read files under the current repository root.",
                Map.of("repoUrl", "string", "path", "string", "action", "string", "keyword", "string", "limit", "integer")), params -> fileOperationTool.operate(
                asString(params.get("repoUrl")),
                asString(params.get("path")),
                asString(params.get("action")),
                asString(params.get("keyword")),
                asInt(params.get("limit"), 20)
        ));

        mcpClient.registerTool(definition(
                "local_file_list",
                "List files under an allowed local directory using optional glob filters.",
                Map.of("path", "string", "filters", "array<string>")), params -> localFileOperationTool.listFiles(
                asString(params.get("path")),
                asStringList(params.get("filters"))
        ));

        mcpClient.registerTool(definition(
                "local_file_read",
                "Read a file from an allowed local directory.",
                Map.of("path", "string")), params -> localFileOperationTool.readFile(
                asString(params.get("path"))
        ));

        mcpClient.registerTool(definition(
                "local_file_write",
                "Write or overwrite a file in an allowed local directory.",
                Map.of("path", "string", "content", "string")), params -> {
                    localFileOperationTool.writeFile(
                            asString(params.get("path")),
                            asString(params.get("content"))
                    );
                    return "OK";
                });

        mcpClient.registerTool(definition(
                "local_file_search",
                "Search files in an allowed local directory by content pattern and optional glob filters.",
                Map.of("path", "string", "pattern", "string", "filters", "array<string>")), params -> localFileOperationTool.searchFiles(
                asString(params.get("path")),
                asString(params.get("pattern")),
                asStringList(params.get("filters"))
        ));

        mcpClient.registerTool(definition(
                "local_file_delete",
                "Delete a file in an allowed local directory.",
                Map.of("path", "string")), params -> {
                    localFileOperationTool.deleteFile(asString(params.get("path")));
                    return "OK";
                });

        mcpClient.registerTool(definition(
                "code_search",
                "Search source files in the current repository by query and optional language.",
                Map.of("repoUrl", "string", "query", "string", "language", "string", "limit", "integer")), params -> codeSearchTool.search(
                asString(params.get("repoUrl")),
                asString(params.get("query")),
                asString(params.get("language")),
                asInt(params.get("limit"), 10)
        ));

        mcpClient.registerTool(definition(
                "web_search",
                "Search external web resources when a provider is configured.",
                Map.of("query", "string", "limit", "integer")), params -> webSearchTool.search(
                asString(params.get("query")),
                asInt(params.get("limit"), 5)
        ));
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

    private String formatNormSearchResult(String query, String projectId, int limit) {
        List<NormRecord> scopedResults = pdfRepository.searchNorms(query, projectId, limit);
        if (!scopedResults.isEmpty()) {
            return formatNormRecords(scopedResults);
        }

        if (projectId != null && !projectId.isBlank()) {
            List<NormRecord> globalResults = pdfRepository.searchNorms(query, "", limit);
            if (!globalResults.isEmpty()) {
                return "当前项目未命中规范，已回退到全局规范库：\n" + formatNormRecords(globalResults);
            }
        }

        int projectNormCount = pdfRepository.listNorms(projectId).size();
        int globalNormCount = pdfRepository.listNorms("").size();
        return "未检索到匹配的规范。"
                + " query=" + preview(query)
                + ", projectId=" + preview(projectId)
                + ", 当前项目规范数=" + projectNormCount
                + ", 全局规范数=" + globalNormCount;
    }

    private String formatNormRecords(List<NormRecord> records) {
        return records.stream()
                .map(item -> item.fileName() + " page " + item.pageNumber() + ": " + item.summary())
                .collect(Collectors.joining("\n"));
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }
}
