package com.heima.codereview.api.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import com.heima.codereview.common.model.review.ReviewTaskDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReviewService reviewService;
    private final String author;

    public PdfExportService(ReviewService reviewService,
                            @Value("${code-review.pdf.author:智能代码审查系统}") String author) {
        this.reviewService = reviewService;
        this.author = author;
    }

    public byte[] exportReview(String reviewId) {
        ReviewTaskDetail detail = reviewService.getById(reviewId);
        ReviewReport report = resolveReport(detail);
        Map<String, String> agentOutputs = resolveAgentOutputs(detail);
        String summary = resolveSummary(detail, agentOutputs);
        String refactoredCode = resolveRefactoredCode(detail, agentOutputs);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = new Font(chineseBaseFont(), 18, Font.BOLD);
            Font headingFont = new Font(chineseBaseFont(), 13, Font.BOLD);
            Font bodyFont = new Font(chineseBaseFont(), 10, Font.NORMAL);
            Font monoFont = FontFactory.getFont(FontFactory.COURIER, 9, Font.NORMAL);

            document.add(new Paragraph("智能代码审查报告", titleFont));
            document.add(new Paragraph("Review ID: " + detail.getReviewId(), bodyFont));
            document.add(new Paragraph("生成时间: " + DATE_TIME_FORMATTER.format(LocalDateTime.now()), bodyFont));
            document.add(new Paragraph("生成者: " + author, bodyFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("一、综合结论", headingFont));
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.addCell(cell("评分", bodyFont));
            summaryTable.addCell(cell(String.valueOf(report.getScore()), bodyFont));
            summaryTable.addCell(cell("问题总数", bodyFont));
            summaryTable.addCell(cell(String.valueOf(report.getTotalIssues()), bodyFont));
            summaryTable.addCell(cell("严重 / 高 / 中 / 低", bodyFont));
            summaryTable.addCell(cell(report.getCriticalCount() + " / "
                    + report.getHighCount() + " / "
                    + report.getMediumCount() + " / "
                    + report.getLowCount(), bodyFont));
            document.add(summaryTable);
            document.add(new Paragraph(safe(summary), bodyFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("二、关键问题", headingFont));
            List<ReviewIssue> issues = report.getIssues() == null ? List.of() : report.getIssues();
            if (issues.isEmpty()) {
                document.add(new Paragraph("未发现关键问题。", bodyFont));
            } else {
                for (int index = 0; index < issues.size(); index++) {
                    ReviewIssue issue = issues.get(index);
                    document.add(new Paragraph((index + 1) + ". [" + issue.severity() + "] "
                            + safe(issue.file()) + ":" + issue.lineNumber(), bodyFont));
                    document.add(new Paragraph("问题: " + safe(issue.message()), bodyFont));
                    document.add(new Paragraph("建议: " + safe(issue.suggestion()), bodyFont));
                    document.add(new Paragraph(" "));
                }
            }

            document.add(new Paragraph("三、代码重构建议", headingFont));
            List<ReviewSuggestion> suggestions = report.getSuggestions() == null ? List.of() : report.getSuggestions();
            if (suggestions.isEmpty()) {
                document.add(new Paragraph("暂无重构建议。", bodyFont));
            } else {
                for (ReviewSuggestion suggestion : suggestions) {
                    document.add(new Paragraph("P" + suggestion.priority() + " - "
                            + safe(suggestion.title()) + ": " + safe(suggestion.description()), bodyFont));
                }
            }
            document.add(new Paragraph(" "));

            document.add(new Paragraph("四、重构代码预览", headingFont));
            document.add(new Paragraph(truncate(refactoredCode, 2000), monoFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("五、协同分析补充", headingFont));
            if (agentOutputs.isEmpty()) {
                document.add(new Paragraph("暂无补充分析。", bodyFont));
            } else {
                agentOutputs.forEach((agent, output) -> {
                    try {
                        document.add(new Paragraph(agent, headingFont));
                        document.add(new Paragraph(truncate(output, 1200), bodyFont));
                    } catch (Exception ignored) {
                    }
                });
            }
        } catch (Exception e) {
            throw new IllegalStateException("PDF 生成失败: " + e.getMessage(), e);
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }

    private com.lowagie.text.pdf.PdfPCell cell(String value, Font font) {
        return new com.lowagie.text.pdf.PdfPCell(new Paragraph(safe(value), font));
    }

    private BaseFont chineseBaseFont() throws Exception {
        return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private ReviewReport resolveReport(ReviewTaskDetail detail) {
        if (detail.getReport() != null) {
            return detail.getReport();
        }
        ReviewReport report = new ReviewReport();
        report.setReviewId(detail.getReviewId());
        return report;
    }

    private Map<String, String> resolveAgentOutputs(ReviewTaskDetail detail) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (detail.getAgentOutputs() != null) {
            detail.getAgentOutputs().forEach((agent, output) -> {
                if (agent != null && !agent.isBlank() && output != null && !output.isBlank()) {
                    resolved.put(agent, output);
                }
            });
        }
        if (!resolved.isEmpty()) {
            return resolved;
        }
        if (detail.getReport() != null && detail.getReport().getSummary() != null && !detail.getReport().getSummary().isBlank()) {
            resolved.put("planner-summary", detail.getReport().getSummary());
        }
        if (detail.getRefactoredCode() != null && !detail.getRefactoredCode().isBlank()) {
            resolved.put("refactor-agent", detail.getRefactoredCode());
        }
        return resolved;
    }

    private String resolveSummary(ReviewTaskDetail detail, Map<String, String> agentOutputs) {
        if (detail.getReport() != null && detail.getReport().getSummary() != null && !detail.getReport().getSummary().isBlank()) {
            return detail.getReport().getSummary();
        }
        String plannerSummary = agentOutputs.get("planner-summary");
        if (hasText(plannerSummary)) {
            return plannerSummary;
        }
        String flowSummary = agentOutputs.get("FlowAgent");
        if (hasText(flowSummary)) {
            return flowSummary;
        }
        return agentOutputs.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> entry.getKey() == null || !entry.getKey().toLowerCase().contains("refactor"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    private String resolveRefactoredCode(ReviewTaskDetail detail, Map<String, String> agentOutputs) {
        if (detail.getRefactoredCode() != null && !detail.getRefactoredCode().isBlank()) {
            return detail.getRefactoredCode();
        }
        String explicitRefactor = agentOutputs.get("refactor-agent");
        if (hasText(explicitRefactor)) {
            return explicitRefactor;
        }
        String streamedRefactor = agentOutputs.get("RefactorAgent");
        if (hasText(streamedRefactor)) {
            return streamedRefactor;
        }
        return agentOutputs.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase().contains("refactor"))
                .map(Map.Entry::getValue)
                .filter(this::hasText)
                .findFirst()
                .orElse("");
    }

    private String truncate(String value, int maxLength) {
        String normalized = safe(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
