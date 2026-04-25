package com.heima.codereview.core.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LLM意图分类器
 * 结合关键词快速预筛 + LLM细粒度分类
 */
@Component
public class LlmIntentClassifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // 复杂度阈值，超过则使用LLM分类
    private static final int KEYWORD_COMPLEXITY_THRESHOLD = 30;

    private final AgentTextGenerator textGenerator;

    public LlmIntentClassifier(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    /**
     * 分析用户消息的意图
     * 简单请求用关键词，复杂请求用LLM
     */
    public IntentClassification analyze(String userMessage, ConversationContext context) {
        // 1. 关键词快速预筛
        List<IntentType> quickCandidates = keywordBasedIntents(userMessage);
        int keywordComplexity = evaluateKeywordComplexity(quickCandidates, context);

        // 2. 简单请求直接返回关键词结果
        if (keywordComplexity < KEYWORD_COMPLEXITY_THRESHOLD || !textGenerator.available()) {
            return IntentClassification.fromQuickResults(quickCandidates);
        }

        // 3. 复杂请求使用LLM细粒度分类
        return llmClassify(userMessage, context);
    }

    /** 基于关键词的意图识别 */
    private List<IntentType> keywordBasedIntents(String userMessage) {
        List<IntentType> intents = new ArrayList<>();
        String normalized = safe(userMessage).toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "review", "审查", "代码质量", "重构", "diff")) {
            intents.add(IntentType.CODE_REVIEW);
        }
        if (containsAny(normalized, "security", "漏洞", "注入", "鉴权", "secret", "xss", "sql")) {
            intents.add(IntentType.SECURITY_ANALYSIS);
        }
        if (containsAny(normalized, "performance", "性能", "慢", "吞吐", "延迟", "复杂度")) {
            intents.add(IntentType.PERFORMANCE_ANALYSIS);
        }
        if (containsAny(normalized, "architecture", "架构", "设计", "分层", "模块", "依赖")) {
            intents.add(IntentType.ARCHITECTURE_ANALYSIS);
        }
        if (containsAny(normalized, "document", "文档", "readme", "注释", "api文档", "说明")) {
            intents.add(IntentType.DOCUMENTATION_GENERATION);
        }
        if (containsAny(normalized, "规范", "标准", "guideline", "best practice", "历史", "memory")) {
            intents.add(IntentType.KNOWLEDGE_RETRIEVAL);
        }

        // 默认意图
        if (intents.isEmpty()) {
            if (normalized.length() <= 18) {
                intents.add(IntentType.SIMPLE_ANSWER);
            } else {
                intents.add(IntentType.GENERAL_CODING);
            }
        }

        return intents;
    }

    /** 根据关键词匹配结果评估复杂度 */
    private int evaluateKeywordComplexity(List<IntentType> intents, ConversationContext context) {
        int score = 15 + intents.size() * 20;
        if (context != null && context.hasRepositoryContext()) {
            score += 15;
        }
        return Math.min(score, 100);
    }

    /** 使用LLM进行细粒度意图分类 */
    private IntentClassification llmClassify(String userMessage, ConversationContext context) {
        String prompt = buildClassificationPrompt(userMessage, context);

        String response = textGenerator.generate(
            "IntentClassifier",
            "你是一个代码审查意图分类器。分析用户消息，判断其意图。",
            prompt,
            Map.of("disableToolCallbacks", true)
        );

        return parseClassification(response);
    }

    /** 构建LLM分类Prompt */
    private String buildClassificationPrompt(String userMessage, ConversationContext context) {
        return """
            用户消息: %s
            仓库上下文: %s

            返回JSON:
            {
              "primary": "CODE_REVIEW|SECURITY_ANALYSIS|PERFORMANCE_ANALYSIS|ARCHITECTURE_ANALYSIS|DOCUMENTATION_GENERATION|KNOWLEDGE_RETRIEVAL|SIMPLE_ANSWER|GENERAL_CODING",
              "candidates": ["主要意图", "候选意图列表"],
              "confidence": 0.0-1.0,
              "rationale": "分类理由",
              "requiresRepository": true|false,
              "complexity": "LOW|MEDIUM|HIGH"
            }
            """.formatted(
            safe(userMessage),
            context != null && context.hasRepositoryContext() ? "有仓库上下文" : "无仓库上下文"
        );
    }

    /** 解析LLM分类响应 */
    private IntentClassification parseClassification(String response) {
        if (response == null || response.isBlank()) {
            return IntentClassification.fromQuickResults(List.of(IntentType.GENERAL_CODING));
        }

        try {
            String json = extractJson(response);
            JsonNode node = OBJECT_MAPPER.readTree(json);

            IntentType primary = IntentType.valueOf(
                safe(node.path("primary").asText()).toUpperCase()
            );

            // 解析候选意图列表
            List<IntentType> candidates = new ArrayList<>();
            JsonNode candidatesNode = node.path("candidates");
            if (candidatesNode.isArray()) {
                for (JsonNode cand : candidatesNode) {
                    try {
                        candidates.add(IntentType.valueOf(safe(cand.asText()).toUpperCase()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            double confidence = node.path("confidence").asDouble(0.5);
            String rationale = safe(node.path("rationale").asText());
            boolean requiresRepo = node.path("requiresRepository").asBoolean(false);
            String complexity = safe(node.path("complexity").asText("LOW"));

            if (candidates.isEmpty()) {
                candidates = List.of(primary);
            }

            return new IntentClassification(primary, candidates, confidence, rationale, requiresRepo, complexity);
        } catch (Exception e) {
            return IntentClassification.fromQuickResults(List.of(IntentType.GENERAL_CODING));
        }
    }

    /** 从响应中提取JSON对象 */
    private String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
