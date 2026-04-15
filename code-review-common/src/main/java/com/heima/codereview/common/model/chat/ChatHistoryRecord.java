package com.heima.codereview.common.model.chat;

import java.util.List;

public record ChatHistoryRecord(
        String id,
        String sessionId,
        String role,
        String content,
        long timestamp,
        List<String> referencedNormFiles
) {
}
