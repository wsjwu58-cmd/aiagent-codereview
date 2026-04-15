package com.heima.codereview.tools.mcp;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class McpToolExecutor {

    private final McpClient mcpClient;

    public McpToolExecutor(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    public Object execute(String toolName, Map<String, Object> params) {
        return mcpClient.callTool(toolName, params);
    }
}
