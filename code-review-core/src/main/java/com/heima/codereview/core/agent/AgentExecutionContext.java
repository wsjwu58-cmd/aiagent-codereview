package com.heima.codereview.core.agent;

import com.heima.codereview.core.chain.ReviewContext;

public record AgentExecutionContext(String sessionId, ReviewContext reviewContext) {
}
