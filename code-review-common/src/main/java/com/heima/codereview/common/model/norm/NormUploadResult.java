package com.heima.codereview.common.model.norm;

public record NormUploadResult(
        String fileId,
        String fileName,
        String projectId,
        int pageCount,
        String description,
        long uploadedAt
) {
}
