package com.heima.codereview.core.agent.reflection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LlmReflectionEvaluator implements ReflectionEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentTextGenerator textGenerator;
    private final ReflectionEvaluator fallbackEvaluator;

    public LlmReflectionEvaluator(AgentTextGenerator textGenerator) {
        this(textGenerator, new KeywordReflectionEvaluator());
    }

    public LlmReflectionEvaluator(AgentTextGenerator textGenerator, ReflectionEvaluator fallbackEvaluator) {
        this.textGenerator = textGenerator;
        this.fallbackEvaluator = fallbackEvaluator == null ? new KeywordReflectionEvaluator() : fallbackEvaluator;
    }

    @Override
    public ReflectionEvaluation evaluate(ReflectionContext context) {
        if (textGenerator == null || !textGenerator.available()) {
            return fallbackEvaluator.evaluate(context);
        }
        String response = textGenerator.generate(
                "ReflectionEvaluator",
                "你是一个专业的代码审查质量评估专家。只返回 JSON，不要调用工具。",
                buildEvaluationPrompt(context),
                Map.of(
                        "scene", "reflection-evaluator",
                        "disableToolCallbacks", true
                )
        );
        if (response == null || response.isBlank()) {
            return fallbackEvaluator.evaluate(context);
        }
        try {
            return parseEvaluation(response);
        } catch (Exception ignored) {
            return fallbackEvaluator.evaluate(context);
        }
    }

    ReflectionEvaluation parseEvaluation(String response) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(extractJson(response));
        boolean satisfied = root.path("satisfied").asBoolean(root.path("isSatisfied").asBoolean(false));
        double confidence = root.path("confidence").asDouble(0.5);
        String gapDescription = firstNonBlank(
                root.path("gapDescription").asText(""),
                root.path("gap").asText("")
        );
        List<String> suggestions = parseSuggestions(root);
        return new ReflectionEvaluation(satisfied, confidence, gapDescription, suggestions);
    }

    private String buildEvaluationPrompt(ReflectionContext context) {
        return """
                请评估以下代码审查子任务的执行结果质量。

                [Specialist]
                %s

                [Original Intent]
                %s

                [Task]
                title=%s
                intent=%s
                description=%s
                depth=%s

                [Execution Result]
                %s

                [Conversation Context]
                repository=%s
                relatedReviews=%s
                relevantNorms=%s

                请从以下维度评估:
                1. 完整性: 是否覆盖任务描述中的关键检查点
                2. 准确性: 结论是否有证据支撑
                3. 深度: 是否有足够专业分析
                4. 可操作性: 建议是否可以执行

                返回 JSON:
                {
                  "satisfied": true,
                  "confidence": 0.0,
                  "gapDescription": "",
                  "improvementSuggestions": []
                }
                """.formatted(
                safe(context == null ? "" : context.specialistId()),
                context == null || context.originalIntent() == null ? "N/A" : context.originalIntent().rationale(),
                context == null || context.task() == null ? "" : safe(context.task().title()),
                context == null || context.task() == null ? "" : context.task().intentType(),
                context == null || context.task() == null ? "" : safe(context.task().description()),
                context == null || context.task() == null ? 0 : context.task().depth(),
                safe(context == null ? "" : context.executionResult()),
                context == null || context.conversationContext() == null ? "N/A" : context.conversationContext().repositorySummary(),
                context == null || context.conversationContext() == null ? "N/A" : context.conversationContext().relatedReviews(),
                context == null || context.conversationContext() == null ? "N/A" : context.conversationContext().relevantNorms()
        );
    }

    private List<String> parseSuggestions(JsonNode root) {
        JsonNode suggestionsNode = root.path("improvementSuggestions");
        if (suggestionsNode.isMissingNode()) {
            suggestionsNode = root.path("suggestions");
        }
        if (!suggestionsNode.isArray()) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        suggestionsNode.forEach(item -> {
            String suggestion = item.asText("");
            if (!suggestion.isBlank()) {
                suggestions.add(suggestion);
            }
        });
        return suggestions;
    }

    private String extractJson(String response) {
        String trimmed = response == null ? "" : response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
