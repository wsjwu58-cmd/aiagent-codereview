package com.heima.codereview.common.model.norm;

public record NormSummary(
        String fileId,
        String fileName,
        String projectId,
        String description,
        int pageCount,
        long uploadedAt
) {
}
