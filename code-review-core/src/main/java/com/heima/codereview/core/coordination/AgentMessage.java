package com.heima.codereview.core.coordination;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentMessage(
        MessageType type,
        String sourceAgentId,
        String targetAgentId,
        String content,
        Map<String, Object> metadata
) {

    public AgentMessage {
        type = type == null ? MessageType.TASK_RESULT : type;
        sourceAgentId = sourceAgentId == null ? "" : sourceAgentId;
        targetAgentId = targetAgentId == null ? "" : targetAgentId;
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
