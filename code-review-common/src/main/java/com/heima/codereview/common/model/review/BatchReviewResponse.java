package com.heima.codereview.common.model.review;

import java.util.Map;

public record BatchReviewResponse(String batchId, Map<String, String> taskStatuses) {
}
