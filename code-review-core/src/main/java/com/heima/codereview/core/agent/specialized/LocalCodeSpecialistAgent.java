package com.heima.codereview.core.agent.specialized;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LocalCodeSpecialistAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_CODE_LENGTH = 12000;

    private final AgentTextGenerator textGenerator;

    public LocalCodeSpecialistAgent(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    public FileAnalysisResult analyzeFile(String folderPath,
                                          String relativePath,
                                          String fileContent,
                                          boolean autoWrite) {
        FileAnalysisResult aiResult = analyzeWithModel(folderPath, relativePath, fileContent, autoWrite);
        if (aiResult != null) {
            return aiResult;
        }
        return heuristicAnalysis(relativePath, fileContent);
    }

    private FileAnalysisResult analyzeWithModel(String folderPath,
                                                String relativePath,
                                                String fileContent,
                                                boolean autoWrite) {
        if (!textGenerator.available()) {
            return null;
        }
        String input = """
                You are reviewing one local source file and optionally preparing an auto-fix.
                Return JSON only.

                {
                  "summary": "short Chinese summary",
                  "issues": [
                    {
                      "severity": "HIGH",
                      "line": 12,
                      "message": "issue description",
                      "suggestion": "fix suggestion"
                    }
                  ],
                  "fixedContent": "optional full updated file content",
                  "rewriteSummary": "optional rewrite summary"
                }

                Rules:
                - Answer in Simplified Chinese.
                - Keep issue count <= 5.
                - fixedContent must be the full file content, not a diff.
                - If no safe fix is available, fixedContent must be an empty string.
                - If autoWrite is false, you may still provide fixedContent as a proposed patch preview.

                [Folder]
                %s

                [Path]
                %s

                [Auto Write]
                %s

                [Code]
                %s
                """.formatted(
                safe(folderPath),
                safe(relativePath),
                autoWrite,
                truncate(fileContent)
        );

        String output = textGenerator.generate(
                "LocalCodeSpecialistAgent",
                "Analyze local code files and prepare safe structured findings.",
                input,
                Map.of(
                        "scene", "local-code",
                        "disableToolCallbacks", true
                )
        );
        if (output == null || output.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(extractJson(output));
            List<Finding> findings = new ArrayList<>();
            JsonNode issues = root.path("issues");
            if (issues.isArray()) {
                for (JsonNode item : issues) {
                    findings.add(new Finding(
                            safe(item.path("severity").asText("LOW")).toUpperCase(),
                            item.path("line").asInt(0),
                            safe(item.path("message").asText()),
                            safe(item.path("suggestion").asText())
                    ));
                }
            }
            String fixedContent = safe(root.path("fixedContent").asText());
            if (fixedContent.equals(fileContent)) {
                fixedContent = "";
            }
            return new FileAnalysisResult(
                    safe(root.path("summary").asText()),
                    findings,
                    fixedContent,
                    safe(root.path("rewriteSummary").asText())
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private FileAnalysisResult heuristicAnalysis(String relativePath, String fileContent) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = safe(fileContent).split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String normalized = line.toLowerCase();
            int lineNumber = index + 1;
            if (normalized.contains("select ")
                    && normalized.contains("+")
                    && normalized.contains("statement")) {
                findings.add(new Finding("HIGH", lineNumber, "疑似通过字符串拼接执行 SQL，存在注入风险。", "优先改为参数化查询或预编译语句。"));
            } else if (normalized.contains("catch (exception")) {
                findings.add(new Finding("MEDIUM", lineNumber, "捕获过宽的 Exception，容易吞掉真实故障上下文。", "缩小异常类型，并补充明确的错误处理。"));
            } else if (normalized.contains("thread.sleep(")) {
                findings.add(new Finding("MEDIUM", lineNumber, "发现阻塞式等待，可能影响吞吐和响应时间。", "考虑使用异步调度、重试策略或等待条件替代固定休眠。"));
            } else if (normalized.contains("system.out.println")) {
                findings.add(new Finding("LOW", lineNumber, "存在直接标准输出日志，生产环境可观测性较弱。", "统一替换为日志框架并带上上下文字段。"));
            } else if (normalized.contains("todo")) {
                findings.add(new Finding("LOW", lineNumber, "检测到 TODO，说明该处实现可能尚未完成。", "在提交前补齐实现或增加明确的跟踪说明。"));
            } else if (normalized.contains("password")
                    && normalized.contains("=")
                    && normalized.contains("\"")) {
                findings.add(new Finding("HIGH", lineNumber, "疑似硬编码敏感凭据。", "将凭据迁移到环境变量或安全配置中心。"));
            }
            if (findings.size() >= 5) {
                break;
            }
        }

        String summary = findings.isEmpty()
                ? relativePath + " 未发现明显的高置信度问题，建议继续结合业务上下文复核。"
                : "已完成 " + relativePath + " 的静态分析，发现 " + findings.size() + " 个值得优先关注的问题。";
        return new FileAnalysisResult(summary, findings, "", "");
    }

    private String truncate(String fileContent) {
        String normalized = safe(fileContent);
        if (normalized.length() <= MAX_CODE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_CODE_LENGTH) + "\n...[truncated]";
    }

    private String extractJson(String text) {
        String trimmed = safe(text).trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record FileAnalysisResult(
            String summary,
            List<Finding> issues,
            String fixedContent,
            String rewriteSummary
    ) {
    }

    public record Finding(
            String severity,
            int line,
            String message,
            String suggestion
    ) {
    }
}
