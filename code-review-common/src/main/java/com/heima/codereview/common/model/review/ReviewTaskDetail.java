package com.heima.codereview.common.model.review;

import java.util.HashMap;
import java.util.Map;

public class ReviewTaskDetail {
    private String reviewId;
    private String sessionId;
    private ReviewStatus status;
    private String codeContent;
    private ReviewReport report;
    private String refactoredCode;
    private Map<String, String> agentOutputs = new HashMap<>();

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = reviewId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public String getCodeContent() {
        return codeContent;
    }

    public void setCodeContent(String codeContent) {
        this.codeContent = codeContent;
    }

    public ReviewReport getReport() {
        return report;
    }

    public void setReport(ReviewReport report) {
        this.report = report;
    }

    public String getRefactoredCode() {
        return refactoredCode;
    }

    public void setRefactoredCode(String refactoredCode) {
        this.refactoredCode = refactoredCode;
    }

    public Map<String, String> getAgentOutputs() {
        return agentOutputs;
    }

    public void setAgentOutputs(Map<String, String> agentOutputs) {
        this.agentOutputs = agentOutputs;
    }
}
