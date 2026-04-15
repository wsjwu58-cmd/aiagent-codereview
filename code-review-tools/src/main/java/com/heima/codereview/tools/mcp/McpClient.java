package com.heima.codereview.tools.mcp;

import com.heima.codereview.tools.exception.ToolExecutionException;
import com.heima.codereview.tools.retry.RetryStrategy;
import com.heima.codereview.tools.retry.RetryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private final RetryTemplate retryTemplate;
    private final Map<String, Function<Map<String, Object>, Object>> toolRegistry = new ConcurrentHashMap<>();
    private final Map<String, McpToolDefinition> toolDefinitions = new ConcurrentHashMap<>();

    public McpClient(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }

    public void registerTool(String toolName, Function<Map<String, Object>, Object> executor) {
        toolRegistry.put(toolName, executor);
        log.info("Registered MCP tool executor: {}", toolName);
    }

    public void registerTool(McpToolDefinition definition, Function<Map<String, Object>, Object> executor) {
        toolRegistry.put(definition.name(), executor);
        toolDefinitions.put(definition.name(), definition);
        log.info("Registered MCP tool: {}", definition.name());
    }

    public Object callTool(String toolName, Map<String, Object> params) {
        Function<Map<String, Object>, Object> executor = toolRegistry.get(toolName);
        if (executor == null) {
            throw new ToolExecutionException("Tool not found: " + toolName);
        }
        return retryTemplate.execute(toolName, RetryStrategy.FIXED_DELAY, () -> executor.apply(params));
    }

    public List<McpToolDefinition> listTools() {
        return toolDefinitions.values().stream()
                .sorted(Comparator.comparing(McpToolDefinition::name))
                .toList();
    }

    public Optional<McpToolDefinition> findTool(String toolName) {
        return Optional.ofNullable(toolDefinitions.get(toolName));
    }
}
