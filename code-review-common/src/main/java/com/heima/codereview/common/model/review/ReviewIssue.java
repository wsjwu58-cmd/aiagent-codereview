package com.heima.codereview.common.model.review;

public record ReviewIssue(
        String id,
        String severity,
        String file,
        int lineNumber,
        String message,
        String ruleId,
        String suggestion
) {
}
