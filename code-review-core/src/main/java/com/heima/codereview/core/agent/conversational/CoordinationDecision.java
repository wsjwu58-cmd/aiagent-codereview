package com.heima.codereview.core.agent.conversational;

import java.util.List;

public record CoordinationDecision(
        CoordinationType type,
        List<String> requiredAgents,
        boolean needRag,
        String rationale
) {
}
