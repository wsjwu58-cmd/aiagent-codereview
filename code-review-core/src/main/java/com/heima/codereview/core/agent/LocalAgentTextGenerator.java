package com.heima.codereview.core.agent;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LocalAgentTextGenerator implements AgentTextGenerator {

    @Override
    public String generate(String agentName, String instruction, String input, Map<String, Object> context) {
        return "[本地降级模式] " + agentName + " 未配置大模型，返回规则引擎结果。";
    }
}
