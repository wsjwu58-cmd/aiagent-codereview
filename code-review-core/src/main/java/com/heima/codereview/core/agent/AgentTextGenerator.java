package com.heima.codereview.core.agent;

import java.util.Map;

public interface AgentTextGenerator {

    default String generate(String agentName, String instruction, String input) {
        return generate(agentName, instruction, input, Map.of());
    }

    String generate(String agentName, String instruction, String input, Map<String, Object> context);

    default boolean available() {
        return false;
    }
}
