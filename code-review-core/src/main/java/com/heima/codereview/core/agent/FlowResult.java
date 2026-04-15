package com.heima.codereview.core.agent;

import com.heima.codereview.common.model.review.ReviewReport;

import java.util.HashMap;
import java.util.Map;

public class FlowResult {
    private ReviewReport report;
    private String advisorOutput;
    private String refactoredCode;
    private String summary;
    private Map<String, String> agentOutputs = new HashMap<>();

    public ReviewReport getReport() {
        return report;
    }

    public void setReport(ReviewReport report) {
        this.report = report;
    }

    public String getAdvisorOutput() {
        return advisorOutput;
    }

    public void setAdvisorOutput(String advisorOutput) {
        this.advisorOutput = advisorOutput;
    }

    public String getRefactoredCode() {
        return refactoredCode;
    }

    public void setRefactoredCode(String refactoredCode) {
        this.refactoredCode = refactoredCode;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, String> getAgentOutputs() {
        return agentOutputs;
    }

    public void setAgentOutputs(Map<String, String> agentOutputs) {
        this.agentOutputs = agentOutputs;
    }
}
