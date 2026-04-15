package com.heima.codereview.common.model.review;

import java.util.List;

public record IncrementalReviewResponse(String reviewId, ReviewStatus status, List<String> changedFiles) {
}
