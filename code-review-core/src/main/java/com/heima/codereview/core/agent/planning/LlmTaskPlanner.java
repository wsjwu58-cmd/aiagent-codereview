package com.heima.codereview.core.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM任务规划器
 * 根据用户请求自主分解和规划任务
 */
@Component
public class LlmTaskPlanner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentTextGenerator textGenerator;

    public LlmTaskPlanner(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    /**
     * 规划任务
     * @param userMessage 用户请求
     * @param context 对话上下文
     * @param intent 意图分析结果
     * @param maxTasks 最大任务数
     */
    public PlannedTasks plan(String userMessage,
                            ConversationContext context,
                            IntentAnalysisResult intent,
                            int maxTasks) {
        // LLM不可用时使用模板降级
        if (!textGenerator.available()) {
            return fallbackToTemplate(intent, maxTasks);
        }

        String prompt = buildPlanningPrompt(userMessage, context, intent, maxTasks);

        String response = textGenerator.generate(
            "TaskPlanner",
            "你是一个代码审查任务规划专家。分析用户请求，分解为可执行的子任务。",
            prompt,
            Map.of("disableToolCallbacks", true)
        );

        return parseResponse(response, intent, maxTasks);
    }

    /** 模板降级：当LLM不可用时 */
    private PlannedTasks fallbackToTemplate(IntentAnalysisResult intent, int maxTasks) {
        List<PlannedTask> tasks = new ArrayList<>();
        int priority = 1;

        for (IntentType type : intent.candidateIntents()) {
            if (tasks.size() >= maxTasks) break;
            tasks.add(createTaskFromIntent(type, priority++));
        }

        ExecutionStrategy strategy = tasks.size() > 1 ? ExecutionStrategy.PARALLEL : ExecutionStrategy.SEQUENTIAL;
        return new PlannedTasks(tasks, strategy, "Fallback to template planning");
    }

    /** 根据意图类型创建任务 */
    private PlannedTask createTaskFromIntent(IntentType type, int priority) {
        String id = "task-" + System.currentTimeMillis() + "-" + priority;
        String title = switch (type) {
            case CODE_REVIEW -> "执行代码审查";
            case SECURITY_ANALYSIS -> "执行安全分析";
            case PERFORMANCE_ANALYSIS -> "执行性能分析";
            case ARCHITECTURE_ANALYSIS -> "分析架构设计";
            case DOCUMENTATION_GENERATION -> "生成文档";
            case KNOWLEDGE_RETRIEVAL -> "检索知识库";
            default -> "执行任务";
        };

        String agentId = switch (type) {
            case CODE_REVIEW -> "review-specialist";
            case SECURITY_ANALYSIS -> "security-specialist";
            case PERFORMANCE_ANALYSIS -> "performance-specialist";
            case ARCHITECTURE_ANALYSIS -> "architecture-specialist";
            case DOCUMENTATION_GENERATION -> "documentation-specialist";
            case KNOWLEDGE_RETRIEVAL -> "rag-specialist";
            default -> "general-coding-agent";
        };

        return new PlannedTask(id, type, title, title, agentId, priority, List.of());
    }

    /** 构建LLM规划Prompt */
    private String buildPlanningPrompt(String userMessage,
                                       ConversationContext context,
                                       IntentAnalysisResult intent,
                                       int maxTasks) {
        String repoInfo = "";
        if (context != null) {
            repoInfo = "仓库: " + safe(context.repoUrl())
                    + ", 语言: " + safe(context.language())
                    + ", 分支: " + safe(context.branch());
        }

        return """
            用户请求: %s
            意图分析: primary=%s, candidates=%s, complexity=%d
            %s

            约束:
            - 最多%d个任务
            - 每个任务必须包含: id, intentType, title, description, agentId, priority, dependencies
            - intentType枚举: CODE_REVIEW, SECURITY_ANALYSIS, PERFORMANCE_ANALYSIS, ARCHITECTURE_ANALYSIS, DOCUMENTATION_GENERATION, KNOWLEDGE_RETRIEVAL
            - agentId枚举: review-specialist, security-specialist, performance-specialist, architecture-specialist, documentation-specialist, rag-specialist, general-coding-agent
            - priority: 1(高优先级)-5(低优先级)
            - dependencies: 依赖的任务ID列表，无依赖则空列表

            返回JSON:
            {
              "tasks": [
                {
                  "id": "task-1",
                  "intentType": "CODE_REVIEW",
                  "title": "任务标题",
                  "description": "任务描述",
                  "agentId": "review-specialist",
                  "priority": 1,
                  "dependencies": []
                }
              ],
              "executionStrategy": "PARALLEL|SEQUENTIAL|MIXED",
              "rationale": "任务规划和执行策略的理由"
            }
            """.formatted(
            safe(userMessage),
            intent.primaryIntent(),
            intent.candidateIntents(),
            intent.complexityScore(),
            repoInfo,
            maxTasks
        );
    }

    /** 解析LLM规划响应 */
    private PlannedTasks parseResponse(String response, IntentAnalysisResult intent, int maxTasks) {
        if (response == null || response.isBlank()) {
            return fallbackToTemplate(intent, maxTasks);
        }

        try {
            String json = extractJson(response);
            JsonNode node = OBJECT_MAPPER.readTree(json);

            List<PlannedTask> tasks = new ArrayList<>();
            JsonNode tasksNode = node.path("tasks");
            if (tasksNode.isArray()) {
                for (JsonNode taskNode : tasksNode) {
                    if (tasks.size() >= maxTasks) break;

                    PlannedTask task = parseTask(taskNode);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }

            if (tasks.isEmpty()) {
                return fallbackToTemplate(intent, maxTasks);
            }

            // 解析执行策略
            ExecutionStrategy strategy;
            String strategyStr = safe(node.path("executionStrategy").asText()).toUpperCase();
            try {
                strategy = ExecutionStrategy.valueOf(strategyStr);
            } catch (IllegalArgumentException e) {
                strategy = tasks.size() > 1 ? ExecutionStrategy.PARALLEL : ExecutionStrategy.SEQUENTIAL;
            }

            String rationale = safe(node.path("rationale").asText());

            return new PlannedTasks(tasks, strategy, rationale);
        } catch (Exception e) {
            return fallbackToTemplate(intent, maxTasks);
        }
    }

    /** 解析单个任务节点 */
    private PlannedTask parseTask(JsonNode node) {
        try {
            String id = safe(node.path("id").asText());
            if (id.isBlank()) return null;

            IntentType intentType;
            try {
                intentType = IntentType.valueOf(safe(node.path("intentType").asText()).toUpperCase());
            } catch (IllegalArgumentException e) {
                intentType = IntentType.GENERAL_CODING;
            }

            String title = safe(node.path("title").asText());
            if (title.isBlank()) title = "任务";

            String description = safe(node.path("description").asText());
            if (description.isBlank()) description = title;

            String agentId = safe(node.path("agentId").asText());
            if (agentId.isBlank()) agentId = "general-coding-agent";

            int priority = node.path("priority").asInt(3);
            if (priority < 1) priority = 3;
            if (priority > 5) priority = 5;

            // 解析依赖列表
            List<String> dependencies = new ArrayList<>();
            JsonNode depsNode = node.path("dependencies");
            if (depsNode.isArray()) {
                for (JsonNode dep : depsNode) {
                    String d = safe(dep.asText());
                    if (!d.isBlank()) {
                        dependencies.add(d);
                    }
                }
            }

            return new PlannedTask(id, intentType, title, description, agentId, priority, dependencies);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
