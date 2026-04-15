package com.heima.codereview.core.agent;

import java.util.Map;

public interface AgentEventListener {
    void onEvent(String sessionId, String eventName, Map<String, Object> data);
}
