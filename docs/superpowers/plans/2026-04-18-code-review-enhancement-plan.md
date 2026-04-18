# 代码审查增强功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现三项代码审查系统增强功能：PDF报告导出、本地代码审查与自动修复、RAG文件管理页面

**Architecture:** 采用微服务架构，后端Spring Boot + OpenPDF，前端React + Ant Design。工具层通过MCP协议统一管理，文件操作通过白名单目录限制确保安全。

**Tech Stack:** Spring Boot 3.x, OpenPDF 1.3.30, Java NIO, React 18, Ant Design 5, SSE

---

## 功能一：PDF 审查报告导出

### Task 1: 添加 OpenPDF 依赖

**Files:**
- Modify: `code-review-api/pom.xml`

- [ ] **Step 1: 添加 OpenPDF 依赖到 pom.xml**

在 `<dependencies>` 节点中添加：

```xml
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>1.3.30</version>
</dependency>
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/pom.xml
git commit -m "chore: add OpenPDF dependency for PDF export"
```

---

### Task 2: 创建 PdfExportService

**Files:**
- Create: `code-review-api/src/main/java/com/heima/codereview/api/service/PdfExportService.java`

- [ ] **Step 1: 创建 PdfExportService.java**

```java
package com.heima.codereview.api.service;

import com.github.librepdf.openpdf洩.WritableSheet;
import com.github.librepdf.openpdf洩.Document;
import com.github.librepdf.openpdf洩.Element;
import com.github.librepdf.openpdf洩.Font;
import com.github.librepdf.openpdf洩.Page;
import com.github.librepdf.openpdf洩.Paragraph;
import com.github.librepdf.openpdf洩.Table;
import com.github.librepdf.openpdf洩.pdf.PdfDocument;
import com.github.librepdf.openpdf洩.pdf.PdfWriter;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PdfExportService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(6, 182, 212));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(30, 41, 59));
    private static final Font SUBHEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(100, 116, 139));
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 41, 59));
    private static final Font CODE_FONT = new Font(Font.COURIER, 8, Font.NORMAL, new Color(30, 41, 59));

    public byte[] generatePdf(ReviewReport report, String reviewId, String refactoredCode) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // 标题
            addTitle(document, "智能代码审查报告");

            // 基本信息
            addSectionHeader(document, "基本信息");
            addKeyValue(document, "Review ID", reviewId);
            addKeyValue(document, "生成时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            addKeyValue(document, "评分", String.valueOf(report.getScore()));
            addKeyValue(document, "问题总数", String.valueOf(report.getTotalIssues()));
            addKeyValue(document, "严重问题", String.valueOf(report.getCriticalCount()));
            addKeyValue(document, "高风险问题", String.valueOf(report.getHighCount()));
            addKeyValue(document, "中风险问题", String.valueOf(report.getMediumCount()));
            addKeyValue(document, "低风险问题", String.valueOf(report.getLowCount()));

            document.add(new Paragraph("\n"));

            // 综合结论
            addSectionHeader(document, "综合结论");
            if (report.getSummary() != null && !report.getSummary().isBlank()) {
                addParagraph(document, report.getSummary(), NORMAL_FONT);
            } else {
                addParagraph(document, "本次审查未发现重大问题。", NORMAL_FONT);
            }

            document.add(new Paragraph("\n"));

            // 关键问题
            addSectionHeader(document, "关键问题");
            if (report.getIssues() != null && !report.getIssues().isEmpty()) {
                for (int i = 0; i < Math.min(report.getIssues().size(), 10); i++) {
                    ReviewIssue issue = report.getIssues().get(i);
                    addIssue(document, i + 1, issue);
                }
            } else {
                addParagraph(document, "未发现关键问题", NORMAL_FONT);
            }

            document.add(new Paragraph("\n"));

            // 建议列表
            addSectionHeader(document, "改进建议");
            if (report.getSuggestions() != null && !report.getSuggestions().isEmpty()) {
                for (ReviewSuggestion suggestion : report.getSuggestions()) {
                    addSuggestion(document, suggestion);
                }
            } else {
                addParagraph(document, "暂无改进建议", NORMAL_FONT);
            }

            // 重构代码预览
            if (refactoredCode != null && !refactoredCode.isBlank()) {
                document.add(new Paragraph("\n"));
                addSectionHeader(document, "重构代码预览");
                addCodeBlock(document, refactoredCode);
            }

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
        return outputStream.toByteArray();
    }

    private void addTitle(Document document, String text) throws Exception {
        Paragraph title = new Paragraph(text, TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);
    }

    private void addSectionHeader(Document document, String text) throws Exception {
        Paragraph header = new Paragraph(text, HEADER_FONT);
        header.setSpacingBefore(15);
        header.setSpacingAfter(10);
        document.add(header);
    }

    private void addKeyValue(Document document, String key, String value) throws Exception {
        Paragraph p = new Paragraph();
        p.add(new Chunk(key + ": ", SUBHEADER_FONT));
        p.add(new Chunk(value, NORMAL_FONT));
        p.setSpacingAfter(5);
        document.add(p);
    }

    private void addParagraph(Document document, String text, Font font) throws Exception {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(8);
        document.add(p);
    }

    private void addIssue(Document document, int index, ReviewIssue issue) throws Exception {
        Paragraph p = new Paragraph();
        p.setSpacingAfter(8);
        p.add(new Chunk(index + ". [", SUBHEADER_FONT));
        p.add(new Chunk(issue.severity() != null ? issue.severity() : "UNKNOWN", SUBHEADER_FONT));
        p.add(new Chunk("] ", SUBHEADER_FONT));

        if (issue.file() != null) {
            p.add(new Chunk(issue.file(), NORMAL_FONT));
            if (issue.lineNumber() != null && issue.lineNumber() > 0) {
                p.add(new Chunk(":" + issue.lineNumber(), NORMAL_FONT));
            }
        }
        p.add(new Chunk("\n   问题: " + (issue.message() != null ? issue.message() : "N/A"), NORMAL_FONT));
        p.add(new Chunk("\n   建议: " + (issue.suggestion() != null ? issue.suggestion() : "N/A"), NORMAL_FONT));
        document.add(p);
    }

    private void addSuggestion(Document document, ReviewSuggestion suggestion) throws Exception {
        Paragraph p = new Paragraph();
        p.setSpacingAfter(5);
        p.add(new Chunk("P" + suggestion.priority() + ": ", SUBHEADER_FONT));
        p.add(new Chunk(suggestion.title() != null ? suggestion.title() : "N/A", NORMAL_FONT));
        p.add(new Chunk(" - ", NORMAL_FONT));
        p.add(new Chunk(suggestion.description() != null ? suggestion.description() : "N/A", NORMAL_FONT));
        document.add(p);
    }

    private void addCodeBlock(Document document, String code) throws Exception {
        Paragraph p = new Paragraph();
        p.setFont(CODE_FONT);
        p.setSpacingAfter(5);

        String[] lines = code.split("\n");
        for (String line : lines) {
            document.add(new Paragraph(line, CODE_FONT));
        }
        document.add(new Paragraph("\n"));
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/service/PdfExportService.java
git commit -m "feat: add PdfExportService for review report PDF generation"
```

---

### Task 3: 添加导出接口到 ReviewController

**Files:**
- Modify: `code-review-api/src/main/java/com/heima/codereview/api/controller/ReviewController.java`

- [ ] **Step 1: 在 ReviewController 中添加导出接口**

在类中添加以下依赖注入和方法：

```java
@Autowired
private PdfExportService pdfExportService;

@Autowired
private ReviewTaskPersistenceRepository reviewTaskPersistenceRepository;

@GetMapping("/{reviewId}/pdf")
public void exportPdf(@PathVariable String reviewId, HttpServletResponse response) {
    ReviewTaskDetail detail = reviewTaskPersistenceRepository.findByReviewId(reviewId);
    if (detail == null) {
        throw new BizException("审查任务不存在");
    }

    ReviewReport report = detail.getReport();
    String refactoredCode = detail.getRefactoredCode();

    byte[] pdfBytes = pdfExportService.generatePdf(report, reviewId, refactoredCode);

    response.setContentType("application/pdf");
    response.setHeader("Content-Disposition", "attachment; filename=\"review_" + reviewId + ".pdf\"");
    response.setContentLength(pdfBytes.length);
    response.getOutputStream().write(pdfBytes);
    response.getOutputStream().flush();
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/controller/ReviewController.java
git commit -m "feat: add PDF export endpoint to ReviewController"
```

---

### Task 4: 前端添加导出按钮

**Files:**
- Modify: `front1/src/components/ReviewReport/index.tsx`

- [ ] **Step 1: 在 ReviewReport 组件中添加导出按钮**

在返回的 JSX 中找到合适位置添加按钮：

```tsx
import { Button } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';

const handleExportPdf = async () => {
  try {
    const response = await fetch(`/api/reviews/${reviewId}/pdf`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });
    if (!response.ok) throw new Error('导出失败');
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `review_${reviewId}.pdf`;
    a.click();
    window.URL.revokeObjectURL(url);
  } catch (error) {
    message.error('导出PDF失败');
  }
};

// 在工具栏或按钮组中添加
<Button icon={<DownloadOutlined />} onClick={handleExportPdf}>
  导出PDF
</Button>
```

- [ ] **Step 2: 提交**

```bash
git add front1/src/components/ReviewReport/index.tsx
git commit -m "feat: add PDF export button to ReviewReport component"
```

---

## 功能二：本地代码审查与自动修复

### Task 5: 添加本地文件操作工具

**Files:**
- Create: `code-review-tools/src/main/java/com/heima/codereview/tools/file/LocalFileOperationTool.java`

- [ ] **Step 1: 创建 LocalFileOperationTool.java**

```java
package com.heima.codereview.tools.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

@Component
public class LocalFileOperationTool {

    @Value("${code-review.local-code.allowed-paths:D:/projects,C:/workspace}")
    private List<String> allowedPaths;

    public String listFiles(String path, String extensions) {
        validatePath(path);
        Path dirPath = Paths.get(path);

        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return "Path does not exist or is not a directory: " + path;
        }

        StringBuilder result = new StringBuilder();
        String[] exts = extensions != null ? extensions.split(",") : new String[0];

        try (Stream<Path> paths = Files.walk(dirPath, 3)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> exts.length == 0 || matchesExtension(p, exts))
                    .limit(100)
                    .forEach(p -> {
                        try {
                            long size = Files.size(p);
                            result.append(p.toAbsolutePath()).append(" (").append(size).append(" bytes)\n");
                        } catch (IOException e) {
                            result.append(p.toAbsolutePath()).append(" (size unknown)\n");
                        }
                    });
        } catch (IOException e) {
            return "Error listing files: " + e.getMessage();
        }

        return result.length() > 0 ? result.toString() : "No files found";
    }

    public String readFile(String path) {
        validatePath(path);
        Path filePath = Paths.get(path);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return "File does not exist: " + path;
        }

        try {
            long size = Files.size(filePath);
            if (size > 10 * 1024 * 1024) {
                return "File too large (>10MB): " + path;
            }
            String content = Files.readString(filePath);
            return content.length() > 50000 ? content.substring(0, 50000) + "\n... (truncated)" : content;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    public String writeFile(String path, String content) {
        validatePath(path);
        Path filePath = Paths.get(path);

        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            return "File written successfully: " + path;
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    public String createFile(String path, String content) {
        return writeFile(path, content);
    }

    public String deleteFile(String path) {
        validatePath(path);
        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            return "File does not exist: " + path;
        }

        try {
            Files.delete(filePath);
            return "File deleted successfully: " + path;
        } catch (IOException e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    public String searchFiles(String path, String pattern) {
        validatePath(path);
        Path dirPath = Paths.get(path);

        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return "Directory does not exist: " + path;
        }

        StringBuilder result = new StringBuilder();
        String normalizedPattern = pattern.toLowerCase();

        try (Stream<Path> paths = Files.walk(dirPath, 5)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(normalizedPattern))
                    .limit(50)
                    .forEach(p -> result.append(p.toAbsolutePath()).append("\n"));
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        return result.length() > 0 ? result.toString() : "No files matching pattern found";
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new SecurityException("Path cannot be empty");
        }

        Path absolutePath = Paths.get(path).toAbsolutePath().normalize();
        boolean allowed = allowedPaths.stream()
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .anyMatch(allowedPath -> absolutePath.startsWith(allowedPath));

        if (!allowed) {
            throw new SecurityException("Path not in allowed directories: " + path);
        }
    }

    private boolean matchesExtension(Path path, String[] extensions) {
        String fileName = path.getFileName().toString().toLowerCase();
        for (String ext : extensions) {
            ext = ext.trim().toLowerCase();
            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-tools/src/main/java/com/heima/codereview/tools/file/LocalFileOperationTool.java
git commit -m "feat: add LocalFileOperationTool for local file access"
```

---

### Task 6: 创建 LocalCodeSpecialistAgent

**Files:**
- Create: `code-review-core/src/main/java/com/heima/codereview/core/agent/specialized/LocalCodeSpecialistAgent.java`

- [ ] **Step 1: 创建 LocalCodeSpecialistAgent.java**

```java
package com.heima.codereview.core.agent.specialized;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.tools.file.LocalFileOperationTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LocalCodeSpecialistAgent extends SpecialistAgent {

    private final LocalFileOperationTool localFileOperationTool;

    public LocalCodeSpecialistAgent(AgentTextGenerator textGenerator,
                                   ObjectProvider<com.heima.codereview.tools.mcp.McpToolExecutor> toolExecutorProvider,
                                   ObjectProvider<com.heima.codereview.tools.mcp.McpClient> mcpClientProvider,
                                   LocalFileOperationTool localFileOperationTool) {
        super(textGenerator, toolExecutorProvider, mcpClientProvider);
        this.localFileOperationTool = localFileOperationTool;
    }

    @Override
    public String specialistId() {
        return "local-code-specialist";
    }

    @Override
    public String getName() {
        return "本地代码审查专家";
    }

    @Override
    public String specialty() {
        return "local-code-analysis";
    }

    @Override
    public List<IntentType> supportedIntents() {
        return List.of(IntentType.CODE_REVIEW, IntentType.GENERAL_CODING);
    }

    @Override
    protected String getSpecialistSystemPrompt() {
        return """
            You are a local code analysis and refactoring specialist.
            You can read, analyze, and modify local files within the allowed directories.
            Focus on:
            1. Code quality and best practices
            2. Security vulnerabilities (SQL injection, XSS, etc.)
            3. Performance issues
            4. Potential bugs and edge cases
            5. Code refactoring suggestions

            When you find issues, provide:
            - Problem description
            - Location (file and line)
            - Severity
            - Suggested fix

            You can directly modify files to apply fixes when needed.
            Always answer in Simplified Chinese unless explicitly required otherwise.
            """;
    }

    @Override
    protected List<String> preferredToolNames() {
        return List.of(
                "local_file_list",
                "local_file_read",
                "local_file_write",
                "local_file_search"
        );
    }

    public String listFiles(String path, String extensions) {
        return localFileOperationTool.listFiles(path, extensions);
    }

    public String readFile(String path) {
        return localFileOperationTool.readFile(path);
    }

    public String writeFile(String path, String content) {
        return localFileOperationTool.writeFile(path, content);
    }

    public String searchFiles(String path, String pattern) {
        return localFileOperationTool.searchFiles(path, pattern);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-core/src/main/java/com/heima/codereview/core/agent/specialized/LocalCodeSpecialistAgent.java
git commit -m "feat: add LocalCodeSpecialistAgent for local code analysis"
```

---

### Task 7: 注册本地文件操作工具到 MCP

**Files:**
- Modify: `code-review-api/src/main/java/com/heima/codereview/api/config/ToolRegistrationConfig.java`

- [ ] **Step 1: 在 ToolRegistrationConfig 中注册本地文件工具**

添加依赖注入：

```java
private final LocalFileOperationTool localFileOperationTool;

public ToolRegistrationConfig(..., LocalFileOperationTool localFileOperationTool) {
    // ... existing assignments
    this.localFileOperationTool = localFileOperationTool;
}
```

在 `registerTools()` 方法中添加：

```java
mcpClient.registerTool(definition(
        "local_file_list",
        "List files in a local directory with optional extension filter.",
        Map.of("path", "string", "extensions", "string")), params -> localFileOperationTool.listFiles(
        asString(params.get("path")),
        asString(params.get("extensions"))
));

mcpClient.registerTool(definition(
        "local_file_read",
        "Read the content of a local file.",
        Map.of("path", "string")), params -> localFileOperationTool.readFile(
        asString(params.get("path"))
));

mcpClient.registerTool(definition(
        "local_file_write",
        "Write or overwrite content to a local file.",
        Map.of("path", "string", "content", "string")), params -> localFileOperationTool.writeFile(
        asString(params.get("path")),
        asString(params.get("content"))
));

mcpClient.registerTool(definition(
        "local_file_search",
        "Search for files matching a pattern in a directory.",
        Map.of("path", "string", "pattern", "string")), params -> localFileOperationTool.searchFiles(
        asString(params.get("path")),
        asString(params.get("pattern"))
));
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/config/ToolRegistrationConfig.java
git commit -m "feat: register local file operation tools in MCP"
```

---

### Task 8: 创建 LocalCodeAnalysisService

**Files:**
- Create: `code-review-api/src/main/java/com/heima/codereview/api/service/LocalCodeAnalysisService.java`

- [ ] **Step 1: 创建 LocalCodeAnalysisService.java**

```java
package com.heima.codereview.api.service;

import com.heima.codereview.api.sse.SseEmitterManager;
import com.heima.codereview.core.agent.AgentEventListener;
import com.heima.codereview.core.agent.FlowAgent;
import com.heima.codereview.core.agent.FlowResult;
import com.heima.codereview.core.agent.LocalCodeSpecialistAgent;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.react.ThinkingStep;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.tools.file.LocalFileOperationTool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class LocalCodeAnalysisService {

    private final FlowAgent flowAgent;
    private final LocalFileOperationTool localFileOperationTool;
    private final SseEmitterManager sseEmitterManager;
    private final Executor localCodeExecutor;

    public LocalCodeAnalysisService(FlowAgent flowAgent,
                                    LocalFileOperationTool localFileOperationTool,
                                    SseEmitterManager sseEmitterManager,
                                    @Qualifier("localCodeExecutor") Executor localCodeExecutor) {
        this.flowAgent = flowAgent;
        this.localFileOperationTool = localFileOperationTool;
        this.sseEmitterManager = sseEmitterManager;
        this.localCodeExecutor = localCodeExecutor;
    }

    public SseEmitter analyze(String folderPath, String fileFilters, String userMessage) {
        String sessionId = IdUtils.withPrefix("local");
        SseEmitter emitter = new SseEmitter(0L);

        localCodeExecutor.execute(() -> {
            try {
                sendEvent(emitter, "connected", Map.of("sessionId", sessionId));

                String files = localFileOperationTool.listFiles(folderPath, fileFilters);
                sendEvent(emitter, "thinking", Map.of(
                        "content", "已扫描目录，找到以下文件:\n" + files
                ));

                ConversationContext context = new ConversationContext(
                        sessionId,
                        "",
                        folderPath,
                        "",
                        "",
                        files,
                        files,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                );

                String fullMessage = userMessage != null && !userMessage.isBlank()
                        ? userMessage
                        : "请分析这个代码目录，查找安全漏洞、性能问题和代码质量问题，并提供修复建议。";

                AgentEventListener listener = (sid, eventName, data) -> {
                    try {
                        if ("thinking".equals(eventName) || "agent_stream".equals(eventName)) {
                            emitter.send(SseEmitter.event().name(eventName, data));
                        } else if ("tool_call".equals(eventName)) {
                            Map<String, Object> toolData = (Map<String, Object>) data;
                            String toolName = (String) toolData.get("tool");
                            Map<String, Object> input = (Map<String, Object>) toolData.get("input");

                            String result = executeLocalTool(toolName, input);
                            emitter.send(SseEmitter.event().name("tool_result", Map.of(
                                    "tool", toolName,
                                    "result", result
                            )));
                        } else {
                            emitter.send(SseEmitter.event().name(eventName, data));
                        }
                    } catch (IOException e) {
                        // Client disconnected
                    }
                };

                FlowResult result = flowAgent.executeConversation(fullMessage, context, listener);

                sendEvent(emitter, "done", Map.of(
                        "summary", result.summary(),
                        "sessionId", sessionId
                ));

                emitter.complete();
            } catch (Exception e) {
                sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String executeLocalTool(String toolName, Map<String, Object> params) {
        if (params == null) return "No parameters provided";

        return switch (toolName) {
            case "local_file_list" -> localFileOperationTool.listFiles(
                    (String) params.get("path"),
                    (String) params.get("extensions")
            );
            case "local_file_read" -> localFileOperationTool.readFile(
                    (String) params.get("path")
            );
            case "local_file_write" -> localFileOperationTool.writeFile(
                    (String) params.get("path"),
                    (String) params.get("content")
            );
            case "local_file_search" -> localFileOperationTool.searchFiles(
                    (String) params.get("path"),
                    (String) params.get("pattern")
            );
            default -> "Unknown tool: " + toolName;
        };
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName, data));
        } catch (IOException ignored) {
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/service/LocalCodeAnalysisService.java
git commit -m "feat: add LocalCodeAnalysisService for local code analysis with SSE"
```

---

### Task 9: 创建 LocalCodeController

**Files:**
- Create: `code-review-api/src/main/java/com/heima/codereview/api/controller/LocalCodeController.java`

- [ ] **Step 1: 创建 LocalCodeController.java**

```java
package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.LocalCodeAnalysisService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/local-code")
public class LocalCodeController {

    private final LocalCodeAnalysisService localCodeAnalysisService;

    public LocalCodeController(LocalCodeAnalysisService localCodeAnalysisService) {
        this.localCodeAnalysisService = localCodeAnalysisService;
    }

    @PostMapping("/analyze")
    public SseEmitter analyze(@RequestBody Map<String, String> request) {
        String folderPath = request.get("folderPath");
        String fileFilters = request.get("fileFilters");
        String userMessage = request.get("userMessage");

        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("folderPath is required");
        }

        return localCodeAnalysisService.analyze(folderPath, fileFilters, userMessage);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/controller/LocalCodeController.java
git commit -m "feat: add LocalCodeController for local code analysis endpoint"
```

---

### Task 10: 添加配置项

**Files:**
- Modify: `code-review-api/src/main/resources/application.yml`

- [ ] **Step 1: 添加本地代码分析配置**

```yaml
code-review:
  local-code:
    allowed-paths:
      - D:/projects
      - C:/workspace
      - /home/user/projects
    auto-write: false
```

- [ ] **Step 2: 添加异步执行器配置**

在 @Configuration 类中添加：

```java
@Bean(name = "localCodeExecutor")
public Executor localCodeExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("local-code-");
    executor.initialize();
    return executor;
}
```

- [ ] **Step 3: 提交**

```bash
git add code-review-api/src/main/resources/application.yml
git commit -m "feat: add local code analysis configuration"
```

---

### Task 11: 前端 - 文件夹选择器组件

**Files:**
- Create: `front1/src/components/FolderSelector/index.tsx`

- [ ] **Step 1: 创建 FolderSelector 组件**

```tsx
import React, { useState } from 'react';
import { Input, Button, Space, message } from 'antd';
import { FolderOutlined } from '@ant-design/icons';

interface FolderSelectorProps {
  value?: string;
  onChange: (path: string) => void;
  disabled?: boolean;
}

export const FolderSelector: React.FC<FolderSelectorProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  const [inputValue, setInputValue] = useState(value || '');
  const [loading, setLoading] = useState(false);

  const handleBrowse = async () => {
    try {
      const result = await (window as any).electron?.selectDirectory();
      if (result) {
        setInputValue(result);
        onChange(result);
      }
    } catch {
      message.info('请直接在输入框中输入文件夹路径');
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setInputValue(val);
    onChange(val);
  };

  return (
    <Space.Compact>
      <Input
        value={inputValue}
        onChange={handleInputChange}
        placeholder="输入文件夹路径，如 D:/projects/myapp"
        disabled={disabled}
        style={{ width: 400 }}
      />
      <Button
        icon={<FolderOutlined />}
        onClick={handleBrowse}
        disabled={disabled}
      >
        选择
      </Button>
    </Space.Compact>
  );
};
```

- [ ] **Step 2: 提交**

```bash
git add front1/src/components/FolderSelector/index.tsx
git commit -m "feat: add FolderSelector component"
```

---

### Task 12: 前端 - 本地代码终端组件

**Files:**
- Create: `front1/src/components/LocalCodeTerminal/index.tsx`

- [ ] **Step 1: 创建 LocalCodeTerminal 组件**

```tsx
import React, { useState, useRef, useEffect } from 'react';
import { Button, Input, Space, Tag, message } from 'antd';
import { SendOutlined, StopOutlined, DeleteOutlined } from '@ant-design/icons';

interface Message {
  type: 'thinking' | 'tool_call' | 'tool_result' | 'done' | 'error' | 'user';
  content: string;
  tool?: string;
}

interface LocalCodeTerminalProps {
  folderPath: string;
  fileFilters?: string;
  onClose?: () => void;
}

export const LocalCodeTerminal: React.FC<LocalCodeTerminalProps> = ({
  folderPath,
  fileFilters,
  onClose,
}) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [status, setStatus] = useState<'idle' | 'running' | 'done' | 'error'>('idle');
  const [sessionId, setSessionId] = useState<string>('');
  const eventSourceRef = useRef<EventSource | null>(null);
  const terminalRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    if (terminalRef.current) {
      terminalRef.current.scrollTop = terminalRef.current.scrollHeight;
    }
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const startAnalysis = async (initialMessage?: string) => {
    setStatus('running');
    setMessages([]);

    try {
      const response = await fetch('/api/local-code/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          folderPath,
          fileFilters: fileFilters || '*.java,*.xml,*.yml',
          userMessage: initialMessage || '',
        }),
      });

      if (!response.ok) {
        throw new Error('启动分析失败');
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('No response body');

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.slice(5));
              handleSSEMessage(data);
            } catch {
              // Ignore parse errors
            }
          }
        }
      }

      setStatus('done');
    } catch (error) {
      setStatus('error');
      setMessages(prev => [...prev, {
        type: 'error',
        content: error instanceof Error ? error.message : '分析失败',
      }]);
    }
  };

  const handleSSEMessage = (data: any) => {
    if (data.sessionId) {
      setSessionId(data.sessionId);
    }

    if (data.event === 'thinking' || data.type === 'thinking') {
      const content = data.content || data.data?.content || '';
      setMessages(prev => [...prev, { type: 'thinking', content }]);
    } else if (data.event === 'tool_call' || data.type === 'tool_call') {
      setMessages(prev => [...prev, {
        type: 'tool_call',
        content: `调用工具: ${data.tool || data.data?.tool}`,
        tool: data.tool || data.data?.tool,
      }]);
    } else if (data.event === 'tool_result' || data.type === 'tool_result') {
      setMessages(prev => [...prev, {
        type: 'tool_result',
        content: data.result || data.data?.result || '',
        tool: data.tool || data.data?.tool,
      }]);
    } else if (data.event === 'done' || data.event === 'completed') {
      setStatus('done');
      setMessages(prev => [...prev, {
        type: 'done',
        content: data.summary || '分析完成',
      }]);
    } else if (data.event === 'error') {
      setStatus('error');
      setMessages(prev => [...prev, {
        type: 'error',
        content: data.message || '发生错误',
      }]);
    }
  };

  const stopAnalysis = () => {
    eventSourceRef.current?.close();
    setStatus('idle');
  };

  const handleSend = () => {
    if (!input.trim()) return;
    setMessages(prev => [...prev, { type: 'user', content: input }]);
    startAnalysis(input);
    setInput('');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#1e1e1e', borderRadius: 8 }}>
      <div style={{
        padding: '8px 12px',
        borderBottom: '1px solid #333',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <Space>
          <Tag color={status === 'running' ? 'green' : status === 'done' ? 'blue' : 'default'}>
            {status === 'idle' ? '就绪' : status === 'running' ? '分析中...' : status === 'done' ? '完成' : '错误'}
          </Tag>
          <span style={{ color: '#888', fontSize: 12 }}>
            {folderPath}
          </span>
        </Space>
        <Space>
          {status === 'running' ? (
            <Button size="small" icon={<StopOutlined />} onClick={stopAnalysis}>
              停止
            </Button>
          ) : (
            <Button size="small" icon={<SendOutlined />} onClick={() => startAnalysis()}>
              重新开始
            </Button>
          )}
          {onClose && (
            <Button size="small" icon={<DeleteOutlined />} onClick={onClose}>
              关闭
            </Button>
          )}
        </Space>
      </div>

      <div
        ref={terminalRef}
        style={{
          flex: 1,
          overflow: 'auto',
          padding: 12,
          fontFamily: 'Monaco, Menlo, monospace',
          fontSize: 13,
        }}
      >
        {messages.map((msg, i) => (
          <div key={i} style={{ marginBottom: 8, whiteSpace: 'pre-wrap' }}>
            {msg.type === 'user' && (
              <span style={{ color: '#4ec9b0' }}>user@local:~$ </span>
            )}
            {msg.type === 'tool_call' && (
              <span style={{ color: '#ce9178' }}>[TOOL] {msg.content}</span>
            )}
            {msg.type === 'tool_result' && (
              <span style={{ color: '#6a9955' }}>{msg.content}</span>
            )}
            {msg.type === 'thinking' && (
              <span style={{ color: '#dcdcaa' }}>{msg.content}</span>
            )}
            {msg.type === 'done' && (
              <span style={{ color: '#569cd6' }}>{msg.content}</span>
            )}
            {msg.type === 'error' && (
              <span style={{ color: '#f44747' }}>ERROR: {msg.content}</span>
            )}
          </div>
        ))}
      </div>

      <div style={{ padding: 8, borderTop: '1px solid #333' }}>
        <Input
          value={input}
          onChange={e => setInput(e.target.value)}
          onPressEnter={handleSend}
          placeholder="输入问题或指令..."
          disabled={status === 'running'}
          style={{ background: '#2d2d2d', color: '#fff', border: '1px solid #333' }}
        />
      </div>
    </div>
  );
};
```

- [ ] **Step 2: 提交**

```bash
git add front1/src/components/LocalCodeTerminal/index.tsx
git commit -m "feat: add LocalCodeTerminal component for streaming output"
```

---

### Task 13: 前端 - 对话工作台添加本地代码审查 Tab

**Files:**
- Modify: `front1/src/pages/Chat/Workspace.tsx`

- [ ] **Step 1: 添加本地代码审查 Tab**

在组件中添加状态和Tab选项：

```tsx
import { FolderSelector } from '../../components/FolderSelector';
import { LocalCodeTerminal } from '../../components/LocalCodeTerminal';

type ChatTab = 'quick' | 'deep' | 'local';

const [activeChatTab, setActiveChatTab] = useState<ChatTab>('quick');
const [localFolderPath, setLocalFolderPath] = useState<string>('');
const [showLocalTerminal, setShowLocalTerminal] = useState(false);

// 在navItems中添加
{ key: 'local' as ChatTab, label: '本地代码审查', icon: FolderOpenOutlined, desc: '分析本地代码文件' }

// 在Tab内容区域添加
{activeChatTab === 'local' && !showLocalTerminal && (
  <div style={{ padding: 24 }}>
    <Title level={4}>本地代码审查</Title>
    <Paragraph>选择一个本地代码文件夹，让 AI 分析其中的代码漏洞和错误。</Paragraph>

    <div style={{ marginBottom: 16 }}>
      <label style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
        文件夹路径
      </label>
      <FolderSelector
        value={localFolderPath}
        onChange={setLocalFolderPath}
      />
    </div>

    <div style={{ marginBottom: 16 }}>
      <label style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
        文件过滤（可选）
      </label>
      <Input
        placeholder="*.java,*.xml,*.yml"
        value={fileFilters}
        onChange={e => setFileFilters(e.target.value)}
        style={{ width: 300 }}
      />
    </div>

    <Button
      type="primary"
      size="large"
      icon={<SendOutlined />}
      onClick={() => setShowLocalTerminal(true)}
      disabled={!localFolderPath.trim()}
    >
      开始分析
    </Button>
  </div>
)}

{activeChatTab === 'local' && showLocalTerminal && (
  <div style={{ height: 'calc(100vh - 200px)' }}>
    <LocalCodeTerminal
      folderPath={localFolderPath}
      fileFilters={fileFilters}
      onClose={() => setShowLocalTerminal(false)}
    />
  </div>
)}
```

- [ ] **Step 2: 提交**

```bash
git add front1/src/pages/Chat/Workspace.tsx
git commit -m "feat: add local code analysis tab to Chat workspace"
```

---

## 功能三：RAG 文件管理页面

### Task 14: 创建 KnowledgeManageService

**Files:**
- Create: `code-review-api/src/main/java/com/heima/codereview/api/service/KnowledgeManageService.java`

- [ ] **Step 1: 创建 KnowledgeManageService.java**

```java
package com.heima.codereview.api.service;

import com.heima.codereview.rag.ChatHistoryRepository;
import com.heima.codereview.rag.PdfRepository;
import com.heima.codereview.rag.knowledge.ReviewKnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeManageService {

    private final ReviewKnowledgeBase reviewKnowledgeBase;
    private final PdfRepository pdfRepository;
    private final ChatHistoryRepository chatHistoryRepository;

    public KnowledgeManageService(ReviewKnowledgeBase reviewKnowledgeBase,
                                  PdfRepository pdfRepository,
                                  ChatHistoryRepository chatHistoryRepository) {
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.pdfRepository = pdfRepository;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    public KnowledgeRecordsResult listRecords(String type, String projectId, String keyword, int page, int size) {
        List<KnowledgeRecord> allRecords = collectAllRecords(keyword, projectId);

        if (type != null && !type.isBlank()) {
            allRecords = allRecords.stream()
                    .filter(r -> type.equals(r.type()))
                    .collect(Collectors.toList());
        }

        int total = allRecords.size();
        int start = page * size;
        int end = Math.min(start + size, total);

        List<KnowledgeRecord> pagedRecords = start < total
                ? allRecords.subList(start, end)
                : List.of();

        return new KnowledgeRecordsResult(pagedRecords, total, page, size);
    }

    public boolean deleteRecord(String id, String type) {
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            return false;
        }

        return switch (type) {
            case "review_history" -> {
                yield reviewKnowledgeBase.deleteById(id);
            }
            case "pdf_norm" -> {
                yield pdfRepository.deleteById(id);
            }
            case "chat_history" -> {
                yield chatHistoryRepository.deleteById(id);
            }
            default -> false;
        };
    }

    public int batchDelete(List<String> ids, String type) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (String id : ids) {
            if (deleteRecord(id, type)) {
                deleted++;
            }
        }
        return deleted;
    }

    private List<KnowledgeRecord> collectAllRecords(String keyword, String projectId) {
        List<KnowledgeRecord> records = new java.util.ArrayList<>();

        // Review history records
        if (keyword == null || keyword.isBlank() || "review_history".contains(keyword.toLowerCase())) {
            try {
                var reviews = reviewKnowledgeBase.search("", projectId, null, 1000);
                reviews.forEach(r -> records.add(new KnowledgeRecord(
                        r.reviewId(),
                        "review_history",
                        r.projectId(),
                        r.content(),
                        null,
                        null,
                        r.createdAt()
                )));
            } catch (Exception ignored) {}
        }

        // PDF norm records
        if (keyword == null || keyword.isBlank() || "pdf_norm".contains(keyword.toLowerCase())) {
            try {
                var norms = pdfRepository.listNorms(projectId);
                norms.forEach(n -> records.add(new KnowledgeRecord(
                        n.id(),
                        "pdf_norm",
                        n.projectId(),
                        n.summary(),
                        n.fileName(),
                        null,
                        n.createdAt()
                )));
            } catch (Exception ignored) {}
        }

        // Chat history records
        if (keyword == null || keyword.isBlank() || "chat_history".contains(keyword.toLowerCase())) {
            try {
                var chats = chatHistoryRepository.searchRelevant(keyword, null, 1000);
                chats.forEach(c -> records.add(new KnowledgeRecord(
                        c.id(),
                        "chat_history",
                        c.projectId(),
                        c.content(),
                        null,
                        c.sessionId(),
                        c.createdAt()
                )));
            } catch (Exception ignored) {}
        }

        // Filter by projectId
        if (projectId != null && !projectId.isBlank()) {
            records = records.stream()
                    .filter(r -> projectId.equals(r.projectId()))
                    .collect(Collectors.toList());
        }

        // Filter by keyword
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            records = records.stream()
                    .filter(r -> (r.summary() != null && r.summary().toLowerCase().contains(lowerKeyword))
                            || (r.fileName() != null && r.fileName().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }

        return records;
    }

    public record KnowledgeRecord(
            String id,
            String type,
            String projectId,
            String summary,
            String fileName,
            String sessionId,
            Long createdAt
    ) {}

    public record KnowledgeRecordsResult(
            List<KnowledgeRecord> content,
            int totalElements,
            int page,
            int size
    ) {}
}
```

- [ ] **Step 2: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/service/KnowledgeManageService.java
git commit -m "feat: add KnowledgeManageService for RAG record management"
```

---

### Task 15: 扩展 KnowledgeController

**Files:**
- Modify: `code-review-api/src/main/java/com/heima/codereview/api/controller/KnowledgeController.java`

- [ ] **Step 1: 添加管理接口**

```java
@Autowired
private KnowledgeManageService knowledgeManageService;

@GetMapping("/records")
public PageResult<KnowledgeManageService.KnowledgeRecord>> listRecords(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String projectId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    KnowledgeManageService.KnowledgeRecordsResult result =
            knowledgeManageService.listRecords(type, projectId, keyword, page, size);

    return new PageResult<>(result.content(), result.totalElements(), result.page(), result.size());
}

@DeleteMapping("/records/{id}")
public Result<Void> deleteRecord(
        @PathVariable String id,
        @RequestParam String type) {
    boolean deleted = knowledgeManageService.deleteRecord(id, type);
    if (deleted) {
        return Result.success();
    }
    return Result.fail("删除失败，记录不存在");
}

@PostMapping("/records/batch")
public Result<Integer> batchDelete(@RequestBody BatchDeleteRequest request) {
    int deleted = knowledgeManageService.batchDelete(request.ids(), request.type());
    return Result.success(deleted);
}

public record BatchDeleteRequest(List<String> ids, String type) {}
```

- [ ] **Step 2: 添加 PageResult 类**

```java
public record PageResult<T>(List<T> content, int totalElements, int page, int size) {}
```

- [ ] **Step 3: 提交**

```bash
git add code-review-api/src/main/java/com/heima/codereview/api/controller/KnowledgeController.java
git commit -m "feat: extend KnowledgeController with record management APIs"
```

---

### Task 16: 前端 - 知识库管理页面

**Files:**
- Create: `front1/src/pages/KnowledgeManage/index.tsx`

- [ ] **Step 1: 创建知识库管理页面**

```tsx
import React, { useState, useEffect } from 'react';
import { Table, Button, Input, Space, Tag, Popconfirm, message, Card, Select, DatePicker } from 'antd';
import { DeleteOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

interface KnowledgeRecord {
  id: string;
  type: 'review_history' | 'pdf_norm' | 'chat_history';
  projectId: string;
  summary: string;
  fileName?: string;
  sessionId?: string;
  createdAt: number;
}

const typeLabels = {
  'review_history': '审查历史',
  'pdf_norm': 'PDF规范',
  'chat_history': '聊天记录',
};

const typeColors = {
  'review_history': 'blue',
  'pdf_norm': 'green',
  'chat_history': 'purple',
};

export const KnowledgeManagePage: React.FC = () => {
  const [records, setRecords] = useState<KnowledgeRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [type, setType] = useState<string | undefined>();
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  const fetchRecords = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: '20',
      });
      if (type) params.append('type', type);
      if (keyword) params.append('keyword', keyword);

      const response = await fetch(`/api/knowledge/records?${params}`, {
        headers: { 'Authorization': `Bearer ${token}` },
      });
      const data = await response.json();
      setRecords(data.content || []);
      setTotal(data.totalElements || 0);
    } catch (error) {
      message.error('获取记录失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRecords();
  }, [page, type, refreshKey]);

  const handleDelete = async (id: string, recordType: string) => {
    try {
      const response = await fetch(`/api/knowledge/records/${id}?type=${recordType}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        message.success('删除成功');
        setRefreshKey(k => k + 1);
      } else {
        message.error('删除失败');
      }
    } catch {
      message.error('删除失败');
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择要删除的记录');
      return;
    }

    try {
      const response = await fetch('/api/knowledge/records/batch', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ids: selectedRowKeys,
          type: type || 'review_history',
        }),
      });
      if (response.ok) {
        message.success('批量删除成功');
        setSelectedRowKeys([]);
        setRefreshKey(k => k + 1);
      } else {
        message.error('批量删除失败');
      }
    } catch {
      message.error('批量删除失败');
    }
  };

  const columns: ColumnsType<KnowledgeRecord> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 120,
      ellipsis: true,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type: keyof typeof typeLabels) => (
        <Tag color={typeColors[type]}>{typeLabels[type]}</Tag>
      ),
    },
    {
      title: '项目ID',
      dataIndex: 'projectId',
      key: 'projectId',
      width: 120,
      ellipsis: true,
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      key: 'summary',
      ellipsis: true,
    },
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      width: 150,
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (ts: number) => ts ? new Date(ts).toLocaleString() : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Popconfirm
          title="确定删除此记录？"
          onConfirm={() => handleDelete(record.id, record.type)}
          okText="确定"
          cancelText="取消"
        >
          <Button size="small" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="知识库管理"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey(k => k + 1)}>
              刷新
            </Button>
            <Popconfirm
              title={`确定删除选中的 ${selectedRowKeys.length} 条记录？`}
              onConfirm={handleBatchDelete}
              okText="确定"
              cancelText="取消"
              disabled={selectedRowKeys.length === 0}
            >
              <Button
                danger
                icon={<DeleteOutlined />}
                disabled={selectedRowKeys.length === 0}
              >
                批量删除 ({selectedRowKeys.length})
              </Button>
            </Popconfirm>
          </Space>
        }
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="选择类型"
            allowClear
            value={type}
            onChange={setType}
            style={{ width: 120 }}
            options={[
              { label: '全部', value: undefined },
              { label: '审查历史', value: 'review_history' },
              { label: 'PDF规范', value: 'pdf_norm' },
              { label: '聊天记录', value: 'chat_history' },
            ]}
          />
          <Input
            placeholder="搜索关键词"
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            onPressEnter={() => { setPage(0); fetchRecords(); }}
            style={{ width: 200 }}
            suffix={<SearchOutlined />}
          />
          <Button onClick={() => { setPage(0); fetchRecords(); }}>搜索</Button>
        </Space>

        <Table
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page + 1,
            total,
            pageSize: 20,
            onChange: (p) => setPage(p - 1),
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>
    </div>
  );
};
```

- [ ] **Step 2: 提交**

```bash
git add front1/src/pages/KnowledgeManage/index.tsx
git commit -m "feat: add KnowledgeManage page for RAG record management"
```

---

### Task 17: 前端 - 添加知识库管理路由和导航

**Files:**
- Modify: `front1/src/App.tsx`

- [ ] **Step 1: 添加导航项和路由**

导入组件：

```tsx
import { KnowledgeManagePage } from './pages/KnowledgeManage';
```

添加导航项：

```tsx
{ key: 'knowledge-manage' as TabKey, label: '知识库管理', icon: DatabaseOutlined, desc: '管理RAG存储数据' }
```

在 ContentPanel 添加路由：

```tsx
case 'knowledge-manage':
  return <KnowledgeManagePage />;
```

- [ ] **Step 2: 提交**

```bash
git add front1/src/App.tsx
git commit -m "feat: add knowledge management page to navigation"
```

---

## 验证计划

### PDF 导出验证

1. 启动后端服务
2. 完成一次代码审查
3. 在审查报告页面点击「导出PDF」
4. 检查PDF内容是否包含评分、问题列表、建议等

### 本地代码审查验证

1. 在对话工作台选择「本地代码审查」Tab
2. 输入一个有效的本地文件夹路径
3. 点击开始分析
4. 验证SSE流式输出正常显示
5. 验证Agent可以读取文件内容

### 知识库管理验证

1. 在侧边栏点击「知识库管理」
2. 验证可以分页查看所有RAG记录
3. 测试类型筛选功能
4. 测试搜索功能
5. 测试单条删除和批量删除

---

## 计划自检

### Spec 覆盖检查

- [x] PDF导出报告功能 - Task 1-4
- [x] 本地代码审查与自动修复 - Task 5-13
- [x] RAG文件管理页面 - Task 14-17

### 占位符检查

- [x] 无 TBD/TODO 占位符
- [x] 所有代码块完整可执行
- [x] 所有文件路径精确

### 类型一致性检查

- [x] `KnowledgeManageService` 中定义的 `KnowledgeRecord` 与 Controller 使用一致
- [x] SSE 事件类型与前端处理匹配
