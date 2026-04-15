package com.heima.codereview.common.model.review;

import java.util.ArrayList;
import java.util.List;

public class ReviewReport {
    private String reviewId;
    private String summary;
    private int score;
    private int totalIssues;
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private List<ReviewIssue> issues = new ArrayList<>();
    private List<ReviewSuggestion> suggestions = new ArrayList<>();
    private List<SimilarCodeGroup> similarCodeGroups = new ArrayList<>();

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = reviewId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public void setTotalIssues(int totalIssues) {
        this.totalIssues = totalIssues;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }

    public int getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(int mediumCount) {
        this.mediumCount = mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public List<ReviewIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ReviewIssue> issues) {
        this.issues = issues;
    }

    public List<ReviewSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ReviewSuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public List<SimilarCodeGroup> getSimilarCodeGroups() {
        return similarCodeGroups;
    }

    public void setSimilarCodeGroups(List<SimilarCodeGroup> similarCodeGroups) {
        this.similarCodeGroups = similarCodeGroups;
    }
}
