package com.heima.codereview.api.service;

import com.heima.codereview.common.model.local.LocalCodeAnalyzeRequest;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.agent.specialized.LocalCodeSpecialistAgent;
import com.heima.codereview.tools.file.LocalFileOperationTool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LocalCodeAnalysisService {

    private final LocalFileOperationTool localFileOperationTool;
    private final LocalCodeSpecialistAgent localCodeSpecialistAgent;
    private final Executor reviewTaskExecutor;
    private final boolean defaultAutoWrite;

    public LocalCodeAnalysisService(LocalFileOperationTool localFileOperationTool,
                                    LocalCodeSpecialistAgent localCodeSpecialistAgent,
                                    @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor,
                                    @Value("${code-review.local-code.auto-write:false}") boolean defaultAutoWrite) {
        this.localFileOperationTool = localFileOperationTool;
        this.localCodeSpecialistAgent = localCodeSpecialistAgent;
        this.reviewTaskExecutor = reviewTaskExecutor;
        this.defaultAutoWrite = defaultAutoWrite;
    }

    public SseEmitter analyze(LocalCodeAnalyzeRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        reviewTaskExecutor.execute(() -> runAnalysis(emitter, request));
        return emitter;
    }

    private void runAnalysis(SseEmitter emitter, LocalCodeAnalyzeRequest request) {
        AtomicInteger step = new AtomicInteger();
        String sessionId = IdUtils.withPrefix("local-code");
        List<String> modifiedFiles = new ArrayList<>();
        int totalIssues = 0;
        try {
            boolean autoWrite = request != null && request.autoWrite() != null
                    ? request.autoWrite()
                    : defaultAutoWrite;
            String folderPath = request == null ? "" : safe(request.folderPath());
            List<String> filters = request == null || request.fileFilters() == null || request.fileFilters().isEmpty()
                    ? List.of("*.java", "*.xml", "*.yml", "*.yaml", "*.properties", "*.md", "*.ts", "*.tsx", "*.js", "*.jsx")
                    : request.fileFilters();

            send(emitter, "connected", Map.of("sessionId", sessionId, "folderPath", folderPath));
            send(emitter, "thinking", thinking(step.incrementAndGet(), "正在扫描本地代码目录..."));

            List<String> files = localFileOperationTool.listFiles(folderPath, filters);
            send(emitter, "tool_call", toolCall("list_files", Map.of("path", folderPath, "filters", filters), files));
            send(emitter, "thinking", thinking(step.incrementAndGet(), "共找到 " + files.size() + " 个候选文件。"));

            int analyzeLimit = Math.min(files.size(), 8);
            for (int index = 0; index < analyzeLimit; index++) {
                String relativePath = files.get(index);
                Path absolutePath = Path.of(folderPath).resolve(relativePath).normalize();
                send(emitter, "thinking", thinking(step.incrementAndGet(), "正在分析 " + relativePath + "..."));

                String content = localFileOperationTool.readFile(absolutePath.toString());
                send(emitter, "tool_call", toolCall("read_file", Map.of("path", relativePath), List.of(preview(content))));

                LocalCodeSpecialistAgent.FileAnalysisResult result = localCodeSpecialistAgent.analyzeFile(
                        folderPath,
                        relativePath,
                        content,
                        autoWrite
                );
                totalIssues += result.issues().size();

                send(emitter, "thinking", thinking(step.incrementAndGet(), result.summary()));
                for (LocalCodeSpecialistAgent.Finding finding : result.issues()) {
                    send(emitter, "thinking", Map.of(
                            "step", step.incrementAndGet(),
                            "content", "[" + finding.severity() + "] "
                                    + relativePath + ":" + finding.line() + " - "
                                    + finding.message() + "；建议：" + finding.suggestion()
                    ));
                }

                if (!result.fixedContent().isBlank() && !result.fixedContent().equals(content)) {
                    if (autoWrite) {
                        localFileOperationTool.writeFile(absolutePath.toString(), result.fixedContent());
                        modifiedFiles.add(relativePath);
                        send(emitter, "file_modified", Map.of("path", relativePath, "action", "modified"));
                    } else {
                        send(emitter, "thinking", thinking(step.incrementAndGet(),
                                "发现可自动修复内容，当前为确认模式，未直接写回文件。"));
                    }
                }
            }

            send(emitter, "done", Map.of(
                    "sessionId", sessionId,
                    "summary", "本地代码扫描完成，共分析 " + Math.min(files.size(), 8) + " 个文件，发现 "
                            + totalIssues + " 个重点问题。",
                    "modifiedFiles", modifiedFiles,
                    "analyzedFiles", Math.min(files.size(), 8),
                    "totalFiles", files.size(),
                    "autoWrite", autoWrite
            ));
            emitter.complete();
        } catch (Exception e) {
            send(emitter, "error", Map.of("message", safe(e.getMessage())));
            emitter.complete();
        }
    }

    private Map<String, Object> thinking(int step, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("content", content);
        return payload;
    }

    private Map<String, Object> toolCall(String tool, Map<String, Object> input, List<String> output) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", tool);
        payload.put("input", input);
        payload.put("output", output);
        return payload;
    }

    private String preview(String content) {
        String normalized = safe(content).replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private void send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException ignored) {
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
