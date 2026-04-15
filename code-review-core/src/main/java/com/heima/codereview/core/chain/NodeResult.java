package com.heima.codereview.core.chain;

import com.heima.codereview.common.model.review.ReviewIssue;

import java.util.ArrayList;
import java.util.List;

public class NodeResult {
    private String nodeName;
    private List<ReviewIssue> issues = new ArrayList<>();

    public NodeResult() {
    }

    public NodeResult(String nodeName, List<ReviewIssue> issues) {
        this.nodeName = nodeName;
        this.issues = issues;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public List<ReviewIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ReviewIssue> issues) {
        this.issues = issues;
    }
}
