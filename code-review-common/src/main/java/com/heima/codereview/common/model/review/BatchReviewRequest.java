package com.heima.codereview.common.model.review;

import java.util.List;

public record BatchReviewRequest(List<CodeSubmission> submissions, String templateId, boolean parallel) {
}
