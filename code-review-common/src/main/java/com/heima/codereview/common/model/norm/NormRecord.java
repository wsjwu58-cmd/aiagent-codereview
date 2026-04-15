package com.heima.codereview.common.model.norm;

import java.util.Map;

public record NormRecord(
        String id,
        String fileName,
        int pageNumber,
        String content,
        String summary,
        Map<String, Object> metadata
) {
}
