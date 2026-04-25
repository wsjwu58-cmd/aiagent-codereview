# Agent Planning & Reflection Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade intent planning from keyword matching to LLM autonomous task planning, and reflection from keyword matching to LLM structured evaluation.

**Architecture:** Replace fixed templates with LLM-powered task planner and evaluator. New components (LlmIntentClassifier, LlmTaskPlanner, LlmReflectionEvaluator) added alongside existing code with config-based switching.

**Tech Stack:** Java 17, Spring Boot, Spring AI, code-review-core

---

## File Structure

```
code-review-core/agent/planning/
├── IntentTaskPlanner.java          # Modify: integrate LLM classifier/planner
├── LlmIntentClassifier.java        # Create: LLM intent classification
├── LlmTaskPlanner.java             # Create: LLM task planning
├── PlannedTasks.java               # Create: planning result record
├── PlannedTask.java                # Create: task record
└── ExecutionStrategy.java          # Create: enum for parallel/sequential/mixed

code-review-core/agent/reflection/
├── KeywordReflectionEvaluator.java  # Keep: fallback option
└── LlmReflectionEvaluator.java     # Create: LLM evaluation

code-review-core/agent/
├── SpecialistAgent.java             # Modify: support evaluator switching
└── FlowAgent.java                  # Modify: support planned tasks execution

code-review-common/
├── result/                         # Check existing patterns for records

application.yml                      # Modify: add config properties
```

---

## Task 1: Create ExecutionStrategy Enum

**Files:**
- Create: `code-review-core/agent/planning/ExecutionStrategy.java`

- [ ] **Step 1: Write the enum**

```java
package com.heima.codereview.core.agent.planning;

public enum ExecutionStrategy {
    PARALLEL,
    SEQUENTIAL,
    MIXED
}
```

- [ ] **Step 2: Commit**

```bash
git add code-review-core/agent/planning/ExecutionStrategy.java
git commit -m "feat: add ExecutionStrategy enum for task planning"
```

---

## Task 2: Create PlannedTask Record

**Files:**
- Create: `code-review-core/agent/planning/PlannedTask.java`

- [ ] **Step 1: Write the record**

```java
package com.heima.codereview.core.agent.planning;

import java.util.List;

public record PlannedTask(
    String id,
    IntentType intentType,
    String title,
    String description,
    String agentId,
    int priority,
    List<String> dependencies
) {
    public PlannedTask {
        if (priority < 1 || priority > 5) {
            priority = 3;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code-review-core/agent/planning/PlannedTask.java
git commit -m "feat: add PlannedTask record for task planning"
```

---

## Task 3: Create PlannedTasks Record

**Files:**
- Create: `code-review-core/agent/planning/PlannedTasks.java`

- [ ] **Step 1: Write the record**

```java
package com.heima.codereview.core.agent.planning;

import java.util.List;

public record PlannedTasks(
    List<PlannedTask> tasks,
    ExecutionStrategy strategy,
    String rationale
) {
    public PlannedTasks {
        if (tasks == null || tasks.isEmpty()) {
            tasks = List.of();
        }
        if (strategy == null) {
            strategy = ExecutionStrategy.SEQUENTIAL;
        }
    }

    public static PlannedTasks empty() {
        return new PlannedTasks(List.of(), ExecutionStrategy.SEQUENTIAL, "");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code-review-core/agent/planning/PlannedTasks.java
git commit -m "feat: add PlannedTasks record for planning results"
```

---

## Task 4: Create IntentClassification Record

**Files:**
- Create: `code-review-core/agent/planning/IntentClassification.java`

- [ ] **Step 1: Write the record**

```java
package com.heima.codereview.core.agent.planning;

import java.util.List;

public record IntentClassification(
    IntentType primary,
    List<IntentType> candidates,
    double confidence,
    String rationale,
    boolean requiresRepository,
    String complexity
) {
    public static IntentClassification fromQuickResults(List<IntentType> intents) {
        IntentType primary = intents.isEmpty() ? IntentType.GENERAL_CODING : intents.get(0);
        return new IntentClassification(
            primary, intents, 0.5, "Quick keyword-based classification",
            false, "LOW"
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code-review-core/agent/planning/IntentClassification.java
git commit -m "feat: add IntentClassification record"
```

---

## Task 5: Create LlmIntentClassifier

**Files:**
- Create: `code-review-core/agent/planning/LlmIntentClassifier.java`
- Test: `code-review-core/src/test/java/com/heima/codereview/core/agent/planning/LlmIntentClassifierTest.java`

- [ ] **Step 1: Write LlmIntentClassifier**

```java
package com.heima.codereview.core.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LlmIntentClassifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double CONFIDENCE_THRESHOLD = 0.7;
    private static final int KEYWORD_COMPLEXITY_THRESHOLD = 30;

    private final AgentTextGenerator textGenerator;

    public LlmIntentClassifier(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    public IntentClassification analyze(String userMessage, ConversationContext context) {
        List<IntentType> quickCandidates = keywordBasedIntents(userMessage);
        int keywordComplexity = evaluateKeywordComplexity(quickCandidates, context);

        if (keywordComplexity < KEYWORD_COMPLEXITY_THRESHOLD || !textGenerator.available()) {
            return IntentClassification.fromQuickResults(quickCandidates);
        }

        return llmClassify(userMessage, context);
    }

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

        if (intents.isEmpty()) {
            if (normalized.length() <= 18) {
                intents.add(IntentType.SIMPLE_ANSWER);
            } else {
                intents.add(IntentType.GENERAL_CODING);
            }
        }

        return intents;
    }

    private int evaluateKeywordComplexity(List<IntentType> intents, ConversationContext context) {
        int score = 15 + intents.size() * 20;
        if (context != null && context.hasRepositoryContext()) {
            score += 15;
        }
        return Math.min(score, 100);
    }

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
```

- [ ] **Step 2: Write unit test**

```java
package com.heima.codereview.core.agent.planning;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmIntentClassifierTest {

    @Mock
    private AgentTextGenerator textGenerator;

    private LlmIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new LlmIntentClassifier(textGenerator);
    }

    @Test
    void shouldReturnQuickResultsWhenLLMUnavailable() {
        when(textGenerator.available()).thenReturn(false);

        IntentClassification result = classifier.analyze("帮我审查代码", null);

        assertEquals(IntentType.CODE_REVIEW, result.primary());
        assertEquals(0.5, result.confidence());
        assertEquals("LOW", result.complexity());
    }

    @Test
    void shouldReturnCorrectIntentForSecurityRequest() {
        when(textGenerator.available()).thenReturn(false);

        IntentClassification result = classifier.analyze("检查SQL注入漏洞", null);

        assertTrue(result.candidates().contains(IntentType.SECURITY_ANALYSIS));
    }

    @Test
    void shouldReturnCorrectIntentForPerformanceRequest() {
        when(textGenerator.available()).thenReturn(false);

        IntentClassification result = classifier.analyze("分析性能问题", null);

        assertTrue(result.candidates().contains(IntentType.PERFORMANCE_ANALYSIS));
    }

    @Test
    void shouldUseLLMWhenComplexityHigh() {
        when(textGenerator.available()).thenReturn(true);
        when(textGenerator.generate(anyString(), anyString(), anyString(), any()))
            .thenReturn("""
                {
                  "primary": "CODE_REVIEW",
                  "candidates": ["CODE_REVIEW", "SECURITY_ANALYSIS"],
                  "confidence": 0.85,
                  "rationale": "用户请求代码审查",
                  "requiresRepository": true,
                  "complexity": "MEDIUM"
                }
                """);

        IntentClassification result = classifier.analyze(
            "请帮我全面审查这个项目的代码安全和性能",
            new ConversationContext("s1", "p1", "", "", "", "", "", List.of(), List.of(), List.of(), List.of())
        );

        assertEquals(IntentType.CODE_REVIEW, result.primary());
        assertEquals(0.85, result.confidence());
        assertTrue(result.candidates().contains(IntentType.SECURITY_ANALYSIS));
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -Dtest=LlmIntentClassifierTest -pl code-review-core`

- [ ] **Step 4: Commit**

```bash
git add code-review-core/agent/planning/LlmIntentClassifier.java code-review-core/src/test/java/com/heima/codereview/core/agent/planning/LlmIntentClassifierTest.java
git commit -m "feat: add LlmIntentClassifier for LLM-based intent classification"
```

---

## Task 6: Create LlmTaskPlanner

**Files:**
- Create: `code-review-core/agent/planning/LlmTaskPlanner.java`
- Test: `code-review-core/src/test/java/com/heima/codereview/core/agent/planning/LlmTaskPlannerTest.java`

- [ ] **Step 1: Write LlmTaskPlanner**

```java
package com.heima.codereview.core.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LlmTaskPlanner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_TASKS = 6;

    private final AgentTextGenerator textGenerator;

    public LlmTaskPlanner(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    public PlannedTasks plan(String userMessage,
                            ConversationContext context,
                            IntentAnalysisResult intent,
                            int maxTasks) {
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

            ExecutionStrategy strategy;
            String strategyStr = safe(taskNode.path("executionStrategy").asText()).toUpperCase();
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
```

- [ ] **Step 2: Write unit test**

```java
package com.heima.codereview.core.agent.planning;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmTaskPlannerTest {

    @Mock
    private AgentTextGenerator textGenerator;

    private LlmTaskPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new LlmTaskPlanner(textGenerator);
    }

    @Test
    void shouldFallbackWhenLLMUnavailable() {
        when(textGenerator.available()).thenReturn(false);

        IntentAnalysisResult intent = new IntentAnalysisResult(
            IntentType.CODE_REVIEW,
            List.of(IntentType.CODE_REVIEW, IntentType.SECURITY_ANALYSIS),
            50, true, true, true, "test"
        );

        PlannedTasks result = planner.plan("审查代码", null, intent, 6);

        assertFalse(result.tasks().isEmpty());
        assertEquals("Fallback to template planning", result.rationale());
    }

    @Test
    void shouldParseLLMResponseCorrectly() {
        when(textGenerator.available()).thenReturn(true);
        when(textGenerator.generate(anyString(), anyString(), anyString(), any()))
            .thenReturn("""
                {
                  "tasks": [
                    {
                      "id": "task-1",
                      "intentType": "CODE_REVIEW",
                      "title": "执行代码审查",
                      "description": "对代码进行全面审查",
                      "agentId": "review-specialist",
                      "priority": 1,
                      "dependencies": []
                    },
                    {
                      "id": "task-2",
                      "intentType": "SECURITY_ANALYSIS",
                      "title": "执行安全分析",
                      "description": "检查安全漏洞",
                      "agentId": "security-specialist",
                      "priority": 2,
                      "dependencies": ["task-1"]
                    }
                  ],
                  "executionStrategy": "SEQUENTIAL",
                  "rationale": "安全分析依赖审查结果"
                }
                """);

        IntentAnalysisResult intent = new IntentAnalysisResult(
            IntentType.CODE_REVIEW,
            List.of(IntentType.CODE_REVIEW, IntentType.SECURITY_ANALYSIS),
            60, true, true, true, "test"
        );

        PlannedTasks result = planner.plan("审查代码安全", null, intent, 6);

        assertEquals(2, result.tasks().size());
        assertEquals(ExecutionStrategy.SEQUENTIAL, result.strategy());
        assertEquals("task-1", result.tasks().get(0).id());
        assertEquals(List.of("task-1"), result.tasks().get(1).dependencies());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -Dtest=LlmTaskPlannerTest -pl code-review-core`

- [ ] **Step 4: Commit**

```bash
git add code-review-core/agent/planning/LlmTaskPlanner.java code-review-core/src/test/java/com/heima/codereview/core/agent/planning/LlmTaskPlannerTest.java
git commit -m "feat: add LlmTaskPlanner for autonomous task planning"
```

---

## Task 7: Create LlmReflectionEvaluator

**Files:**
- Create: `code-review-core/agent/reflection/LlmReflectionEvaluator.java`
- Test: `code-review-core/src/test/java/com/heima/codereview/core/agent/reflection/LlmReflectionEvaluatorTest.java`

- [ ] **Step 1: Write LlmReflectionEvaluator**

```java
package com.heima.codereview.core.agent.reflection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.planning.SubTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LlmReflectionEvaluator implements ReflectionEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double QUALITY_THRESHOLD = 0.7;

    private final AgentTextGenerator textGenerator;

    public LlmReflectionEvaluator(AgentTextGenerator textGenerator) {
        this.textGenerator = textGenerator;
    }

    @Override
    public ReflectionEvaluation evaluate(ReflectionContext context) {
        if (!textGenerator.available()) {
            return fallbackEvaluate(context);
        }

        String prompt = buildEvaluationPrompt(context);

        String response = textGenerator.generate(
            "ReflectionEvaluator",
            "你是代码审查反思评估专家。评估Agent的执行结果是否满足质量要求。",
            prompt,
            Map.of("disableToolCallbacks", true)
        );

        return parseResponse(response);
    }

    private ReflectionEvaluation fallbackEvaluate(ReflectionContext context) {
        String result = context == null || context.executionResult() == null
            ? ""
            : context.executionResult().toLowerCase();

        if (requiresFollowUp(result)) {
            return ReflectionEvaluation.gap(
                result.isBlank() ? 0.2 : 0.45,
                "The current answer is incomplete or lacks evidence.",
                List.of("Close the remaining gap with more evidence.")
            );
        }
        return ReflectionEvaluation.satisfied(0.9);
    }

    private boolean requiresFollowUp(String normalized) {
        return normalized.isBlank()
            || normalized.contains("insufficient")
            || normalized.contains("缺少")
            || normalized.contains("无法")
            || normalized.contains("unavailable")
            || normalized.contains("需要更多");
    }

    private String buildEvaluationPrompt(ReflectionContext context) {
        String taskInfo = "";
        if (context != null && context.task() != null) {
            SubTask task = context.task();
            taskInfo = "- 任务类型: " + task.intentType()
                    + "\n- 任务描述: " + task.description()
                    + "\n- 执行深度: " + task.depth();
        }

        String result = context == null ? "" : safe(context.executionResult());

        return """
            任务信息:
            %s

            执行结果:
            %s

            评估标准:
            1. 完整性: 结果是否完整回答了用户问题？
            2. 证据充分性: 是否有代码引用、具体文件位置、证据支持？
            3. 一致性: 是否与历史审查结论矛盾？
            4. 专业性: 是否正确使用安全/性能等专业术语？

            返回JSON:
            {
              "satisfied": true|false,
              "score": 0.0-1.0,
              "gapDescription": "如果satisfied=false，具体说明差距",
              "improvementSuggestions": ["改进建议列表"]
            }
            """.formatted(taskInfo, result);
    }

    private ReflectionEvaluation parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return ReflectionEvaluation.satisfied(0.5);
        }

        try {
            String json = extractJson(response);
            JsonNode node = OBJECT_MAPPER.readTree(json);

            boolean satisfied = node.path("satisfied").asBoolean(false);
            double score = node.path("score").asDouble(0.5);
            String gapDescription = safe(node.path("gapDescription").asText());
            if (gapDescription.isBlank() && !satisfied) {
                gapDescription = "Execution result does not meet quality threshold";
            }

            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = node.path("improvementSuggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode suggestion : suggestionsNode) {
                    String s = safe(suggestion.asText());
                    if (!s.isBlank()) {
                        suggestions.add(s);
                    }
                }
            }

            if (!satisfied && suggestions.isEmpty()) {
                suggestions.add("Close the remaining gap with more evidence.");
            }

            return new ReflectionEvaluation(satisfied, score, gapDescription, suggestions);
        } catch (Exception e) {
            return ReflectionEvaluation.satisfied(0.5);
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
```

- [ ] **Step 2: Write unit test**

```java
package com.heima.codereview.core.agent.reflection;

import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.planning.SubTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmReflectionEvaluatorTest {

    @Mock
    private AgentTextGenerator textGenerator;

    private LlmReflectionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new LlmReflectionEvaluator(textGenerator);
    }

    @Test
    void shouldFallbackWhenLLMUnavailable() {
        when(textGenerator.available()).thenReturn(false);

        ReflectionContext context = new ReflectionContext(
            "security-specialist",
            new IntentAnalysisResult(IntentType.SECURITY_ANALYSIS, List.of(), 50, true, true, true, ""),
            SubTask.of("t1", IntentType.SECURITY_ANALYSIS, "title", "desc", "agent", 0, ""),
            "需要更多证据",
            null
        );

        ReflectionEvaluation result = evaluator.evaluate(context);

        assertFalse(result.satisfied());
        assertEquals(0.45, result.score());
    }

    @Test
    void shouldReturnSatisfiedWhenResultGood() {
        when(textGenerator.available()).thenReturn(true);
        when(textGenerator.generate(anyString(), anyString(), anyString(), any()))
            .thenReturn("""
                {
                  "satisfied": true,
                  "score": 0.9,
                  "gapDescription": "",
                  "improvementSuggestions": []
                }
                """);

        ReflectionContext context = new ReflectionContext(
            "review-specialist",
            new IntentAnalysisResult(IntentType.CODE_REVIEW, List.of(), 50, true, true, true, ""),
            SubTask.of("t1", IntentType.CODE_REVIEW, "title", "desc", "agent", 0, ""),
            "代码审查完成，发现3个问题：1. SQL注入风险 2. 缺少参数校验",
            null
        );

        ReflectionEvaluation result = evaluator.evaluate(context);

        assertTrue(result.satisfied());
        assertEquals(0.9, result.score());
    }

    @Test
    void shouldReturnGapWhenInsufficient() {
        when(textGenerator.available()).thenReturn(true);
        when(textGenerator.generate(anyString(), anyString(), anyString(), any()))
            .thenReturn("""
                {
                  "satisfied": false,
                  "score": 0.4,
                  "gapDescription": "缺少代码证据支持",
                  "improvementSuggestions": ["添加具体的代码片段作为证据"]
                }
                """);

        ReflectionContext context = new ReflectionContext(
            "review-specialist",
            new IntentAnalysisResult(IntentType.CODE_REVIEW, List.of(), 50, true, true, true, ""),
            SubTask.of("t1", IntentType.CODE_REVIEW, "title", "desc", "agent", 0, ""),
            "代码有问题",
            null
        );

        ReflectionEvaluation result = evaluator.evaluate(context);

        assertFalse(result.satisfied());
        assertEquals(0.4, result.score());
        assertFalse(result.improvementSuggestions().isEmpty());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -Dtest=LlmReflectionEvaluatorTest -pl code-review-core`

- [ ] **Step 4: Commit**

```bash
git add code-review-core/agent/reflection/LlmReflectionEvaluator.java code-review-core/src/test/java/com/heima/codereview/core/agent/reflection/LlmReflectionEvaluatorTest.java
git commit -m "feat: add LlmReflectionEvaluator for structured evaluation"
```

---

## Task 8: Modify SpecialistAgent - Add Configurable Evaluator

**Files:**
- Modify: `code-review-core/agent/SpecialistAgent.java`

- [ ] **Step 1: Review current implementation**

Read lines 21-40 of SpecialistAgent.java to understand current constructor.

- [ ] **Step 2: Add configuration**

Add to SpecialistAgent.java:

```java
public abstract class SpecialistAgent extends SpecializedAgent implements SelfReflection {

    private static final int MAX_REFLECTION_DEPTH = 2;

    private final ReflectionEvaluator reflectionEvaluator;
    private final int maxReflectionDepth;

    protected SpecialistAgent(AgentTextGenerator textGenerator,
                              ObjectProvider<McpToolExecutor> toolExecutorProvider,
                              ObjectProvider<McpClient> mcpClientProvider) {
        this(textGenerator, toolExecutorProvider, mcpClientProvider,
             createDefaultEvaluator(textGenerator));
    }

    protected SpecialistAgent(AgentTextGenerator textGenerator,
                              ObjectProvider<McpToolExecutor> toolExecutorProvider,
                              ObjectProvider<McpClient> mcpClientProvider,
                              ReflectionEvaluator reflectionEvaluator) {
        super(textGenerator, toolExecutorProvider, mcpClientProvider);
        this.reflectionEvaluator = reflectionEvaluator == null
            ? createDefaultEvaluator(textGenerator)
            : reflectionEvaluator;
        this.maxReflectionDepth = MAX_REFLECTION_DEPTH;
    }

    private static ReflectionEvaluator createDefaultEvaluator(AgentTextGenerator textGenerator) {
        if (textGenerator != null && textGenerator.available()) {
            return new LlmReflectionEvaluator(textGenerator);
        }
        return new KeywordReflectionEvaluator();
    }

    // ... keep existing methods ...
}
```

- [ ] **Step 3: Commit**

```bash
git add code-review-core/agent/SpecialistAgent.java
git commit -m "feat: add configurable reflection evaluator to SpecialistAgent"
```

---

## Task 9: Modify IntentTaskPlanner - Integrate LLM Components

**Files:**
- Modify: `code-review-core/agent/planning/IntentTaskPlanner.java`

- [ ] **Step 1: Review current implementation**

Read full IntentTaskPlanner.java to understand current logic.

- [ ] **Step 2: Add LLM components and modify analyzeIntent**

Add fields and modify IntentTaskPlanner:

```java
@Component
public class IntentTaskPlanner {

    private static final int COMPLEXITY_THRESHOLD = 40;

    private final LlmIntentClassifier intentClassifier;
    private final LlmTaskPlanner taskPlanner;

    public IntentTaskPlanner(LlmIntentClassifier intentClassifier,
                            LlmTaskPlanner taskPlanner) {
        this.intentClassifier = intentClassifier;
        this.taskPlanner = taskPlanner;
    }

    // ... keep existing methods ...

    public IntentAnalysisResult analyzeIntent(String userMessage, ConversationContext context, boolean forceReview) {
        // Use LLM classifier for complex requests
        if (forceReview || shouldUseLlmClassification(context)) {
            return analyzeWithLLM(userMessage, context, forceReview);
        }

        // Fallback to keyword-based for simple requests
        return analyzeWithKeywords(userMessage, context, forceReview);
    }

    private boolean shouldUseLlmClassification(ConversationContext context) {
        return context != null && context.hasRepositoryContext();
    }

    private IntentAnalysisResult analyzeWithLLM(String userMessage, ConversationContext context, boolean forceReview) {
        IntentClassification classification = intentClassifier.analyze(userMessage, context);

        IntentType primary = classification.primary();
        List<IntentType> intents = new ArrayList<>(classification.candidates());

        if (!intents.contains(primary)) {
            intents.add(0, primary);
        }

        int complexityScore = Math.min(100,
                15 + intents.size() * 20
                + (context != null && context.hasRepositoryContext() ? 15 : 0)
                + (hasRelevantNorms(context) ? 10 : 0)
                + (classification.confidence() < 0.7 ? 15 : 0));

        boolean requiresRepositoryContext = forceReview || classification.requiresRepository()
                || primary == IntentType.CODE_REVIEW
                || primary == IntentType.ARCHITECTURE_ANALYSIS;

        boolean requiresReflection = complexityScore >= 30 || intents.size() > 1;
        boolean requiresBacktracking = complexityScore >= 50 || intents.size() > 2;

        String rationale = "Intent=" + primary
                + ", candidates=" + intents
                + ", confidence=" + classification.confidence()
                + ", llmClassified=true";

        return new IntentAnalysisResult(
                primary, intents, complexityScore,
                requiresRepositoryContext, requiresReflection, requiresBacktracking,
                rationale
        );
    }

    // ... keep existing helper methods ...
}
```

- [ ] **Step 3: Add buildInitialTasks with LLM planning**

```java
public List<SubTask> buildInitialTasks(IntentAnalysisResult intent) {
    if (intent == null) {
        return List.of();
    }

    // For complex requests, use LLM task planner
    if (intent.complexityScore() >= COMPLEXITY_THRESHOLD && taskPlanner != null) {
        // Will be called by FlowAgent with context
        return List.of();
    }

    // Fallback to template for simple requests
    List<SubTask> tasks = new ArrayList<>();
    IntentType primary = intent.primaryIntent();
    tasks.add(createPrimaryTask(primary));

    if (intent.candidateIntents().size() > 1) {
        for (IntentType candidate : intent.candidateIntents()) {
            if (candidate != primary && isParallelizable(candidate)) {
                tasks.add(createPrimaryTask(candidate));
            }
        }
    }
    return tasks;
}

public PlannedTasks planTasks(String userMessage,
                              ConversationContext context,
                              IntentAnalysisResult intent,
                              int maxTasks) {
    if (taskPlanner == null || intent == null) {
        return PlannedTasks.empty();
    }
    return taskPlanner.plan(userMessage, context, intent, maxTasks);
}
```

- [ ] **Step 4: Commit**

```bash
git add code-review-core/agent/planning/IntentTaskPlanner.java
git commit -m "feat: integrate LLM classifier and planner into IntentTaskPlanner"
```

---

## Task 10: Modify FlowAgent - Support Planned Tasks Execution

**Files:**
- Modify: `code-review-core/agent/FlowAgent.java`

- [ ] **Step 1: Review current implementation**

Read FlowAgent.java lines 77-167 to understand execute() method.

- [ ] **Step 2: Modify to support planned tasks**

Add to FlowAgent:

```java
private static final int COMPLEXITY_THRESHOLD = 40;

public FlowResult execute(AgentExecutionContext context, AgentEventListener listener) {
    // ... existing initialization code ...

    IntentAnalysisResult intent = intentTaskPlanner.analyzeIntent(userMessage, conversationContext, true);

    // ... existing event listeners ...

    List<SpecialistReport> parallelReports = new ArrayList<>();
    List<SpecialistExecutionResult> parallelResults = new ArrayList<>();
    List<TaskExecutionResult> allTaskResults = new ArrayList<>();
    List<ThinkingStep> allSteps = new ArrayList<>();

    // Use LLM planned tasks for complex requests
    if (intent.complexityScore() >= COMPLEXITY_THRESHOLD) {
        PlannedTasks planned = intentTaskPlanner.planTasks(
            userMessage, conversationContext, intent, MAX_PLANNED_TASKS);

        if (!planned.tasks().isEmpty()) {
            // Execute based on strategy
            switch (planned.strategy()) {
                case PARALLEL:
                    List<SubTask> parallelTasks = convertToSubTasks(planned.tasks());
                    parallelReports = runParallelSpecialists(
                        parallelTasks, userMessage, conversationContext, intent,
                        createBridgeListener(sessionId, listener), parallelResults, allTaskResults, allSteps);
                    break;
                case MIXED:
                case SEQUENTIAL:
                default:
                    PlanningExecution execution = runPlannerSequential(
                        userMessage, conversationContext, intent,
                        convertToSubTasks(planned.tasks()),
                        createBridgeListener(sessionId, listener),
                        new ExecutionHistory(),
                        MAX_PLANNED_TASKS);
                    parallelReports = execution.reports();
                    allTaskResults = execution.taskResults();
                    allSteps = execution.steps();
                    break;
            }
        }
    } else {
        // Simple requests: use template approach
        List<SubTask> initialTasks = buildInitialTasks(intent);
        List<SubTask> supplementalTasks = initialTasks.stream()
                .filter(task -> task.intentType() != IntentType.CODE_REVIEW)
                .toList();

        if (!supplementalTasks.isEmpty()) {
            parallelReports = runParallelSpecialists(
                supplementalTasks, userMessage, conversationContext, intent,
                createBridgeListener(sessionId, listener), parallelResults, allTaskResults, allSteps);
        }
    }

    // ... rest of existing code ...
}

private List<SubTask> convertToSubTasks(List<PlannedTask> plannedTasks) {
    return plannedTasks.stream()
        .map(pt -> SubTask.of(
            pt.id(),
            pt.intentType(),
            pt.title(),
            pt.description(),
            pt.agentId(),
            pt.priority() - 1,
            ""
        ))
        .toList();
}
```

- [ ] **Step 3: Commit**

```bash
git add code-review-core/agent/FlowAgent.java
git commit -m "feat: add support for LLM planned tasks execution in FlowAgent"
```

---

## Task 11: Add Configuration Properties

**Files:**
- Modify: `code-review-start/src/main/resources/application.yml`

- [ ] **Step 1: Add new configuration**

Add to application.yml:

```yaml
agent:
  planning:
    enable-llm-planning: true
    complexity-threshold: 40
    max-tasks: 6
  reflection:
    evaluator-type: llm
    max-reflection-depth: 3
```

- [ ] **Step 2: Create configuration properties class**

Create: `code-review-core/agent/config/AgentPlanningProperties.java`

```java
package com.heima.codereview.core.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.planning")
public class AgentPlanningProperties {
    private boolean enableLlmPlanning = true;
    private int complexityThreshold = 40;
    private int maxTasks = 6;
}

@Component
@ConfigurationProperties(prefix = "agent.reflection")
public class AgentReflectionProperties {
    private String evaluatorType = "llm";
    private int maxReflectionDepth = 3;
}
```

- [ ] **Step 3: Commit**

```bash
git add code-review-start/src/main/resources/application.yml
git add code-review-core/agent/config/AgentPlanningProperties.java
git add code-review-core/agent/config/AgentReflectionProperties.java
git commit -m "feat: add agent planning and reflection configuration properties"
```

---

## Task 12: Integration Test

**Files:**
- Create: `code-review-core/src/test/java/com/heima/codereview/core/agent/FlowAgentIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.heima.codereview.core.agent;

import com.heima.codereview.core.agent.planning.*;
import com.heima.codereview.core.chain.ReviewContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlowAgentIntegrationTest {

    @Autowired
    private IntentTaskPlanner intentTaskPlanner;

    @Test
    void shouldUseLLMClassifierForComplexRequests() {
        String message = "请帮我全面审查这个Java项目的代码安全和性能问题，重点关注SQL注入和性能瓶颈";

        ConversationContext context = new ConversationContext(
            "session-1", "project-1", "https://github.com/test/repo", "main",
            "java", "", "", List.of(), List.of(), List.of(), List.of()
        );

        IntentAnalysisResult result = intentTaskPlanner.analyzeIntent(message, context, false);

        assertNotNull(result);
        assertTrue(result.complexityScore() >= 40);
        assertTrue(result.requiresReflection());
    }

    @Test
    void shouldPlanTasksWithLLM() {
        String message = "审查代码安全";

        ConversationContext context = new ConversationContext(
            "session-1", "project-1", "https://github.com/test/repo", "main",
            "java", "", "", List.of(), List.of(), List.of(), List.of()
        );

        IntentAnalysisResult intent = new IntentAnalysisResult(
            IntentType.CODE_REVIEW,
            List.of(IntentType.CODE_REVIEW, IntentType.SECURITY_ANALYSIS),
            60, true, true, true, "test"
        );

        PlannedTasks planned = intentTaskPlanner.planTasks(message, context, intent, 6);

        assertNotNull(planned);
        assertFalse(planned.tasks().isEmpty());
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `mvn test -Dtest=FlowAgentIntegrationTest -pl code-review-core`

- [ ] **Step 3: Commit**

```bash
git add code-review-core/src/test/java/com/heima/codereview/core/agent/FlowAgentIntegrationTest.java
git commit -m "test: add integration tests for LLM planning and reflection"
```

---

## Verification Checklist

- [ ] All new classes compile
- [ ] All unit tests pass
- [ ] Integration test passes
- [ ] Backward compatibility: simple requests still use template
- [ ] Fallback works when LLM unavailable

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-25-agent-planning-reflection-upgrade.md`**

Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
