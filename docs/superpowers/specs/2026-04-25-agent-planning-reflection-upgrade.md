# 任务规划与反思机制升级设计

## 1. 概述

### 1.1 目标
升级意图规划和反思机制，实现：
- **意图规划**: 从固定模板升级为LLM全自主任务规划
- **反思机制**: 从关键词匹配升级为LLM结构化评估

### 1.2 改动范围
- `code-review-core/agent/planning/IntentTaskPlanner` - 重构
- `code-review-core/agent/planning/LlmIntentClassifier` - 新增
- `code-review-core/agent/planning/LlmTaskPlanner` - 新增
- `code-review-core/agent/reflection/LlmReflectionEvaluator` - 新增
- `code-review-core/agent/SpecialistAgent` - 适配
- `code-review-core/agent/FlowAgent` - 适配

---

## 2. 架构设计

### 2.1 整体流程

```
用户请求
    │
    ▼
IntentTaskPlanner.analyzeIntent()
    │
    ├─→ 关键词快速预筛 → 候选Intent列表
    │
    ├─→ 复杂度评估 → complexityScore
    │
    └─→ if (complexityScore >= 阈值 && 启用LLM规划)
            │
            ▼
        LlmTaskPlanner.plan()
            │
            ├─→ LLM分析请求
            ├─→ 分解子任务
            ├─→ 排优先级
            ├─→ 确定依赖
            │
            ▼
        PlannedTasks {tasks[], executionStrategy, rationale}
            │
            ▼
        FlowAgent 执行

        else (简单请求)
            │
            ▼
        快速模板返回 (向后兼容)
```

### 2.2 意图分类升级

#### LlmIntentClassifier

```java
public class LlmIntentClassifier {

    public IntentClassification analyze(String userMessage, ConversationContext context) {
        // 1. 关键词快速预筛
        List<IntentType> quickCandidates = keywordBasedIntents(userMessage);

        // 2. 复杂度评估
        int complexity = evaluateComplexity(quickCandidates, context);

        // 3. 简单请求直接返回
        if (complexity < COMPLEXITY_THRESHOLD) {
            return IntentClassification.fromQuickResults(quickCandidates);
        }

        // 4. LLM细粒度分类
        return llmClassify(userMessage, context);
    }
}
```

#### Prompt

```
你是一个代码审查意图分类器。分析用户消息，判断其意图。

用户消息: {userMessage}
仓库上下文: {hasRepoContext}

返回JSON:
{
  "primary": "CODE_REVIEW|SECURITY_ANALYSIS|PERFORMANCE_ANALYSIS|ARCHITECTURE_ANALYSIS|DOCUMENTATION_GENERATION|KNOWLEDGE_RETRIEVAL|SIMPLE_ANSWER|GENERAL_CODING",
  "candidates": ["主要意图", "候选意图列表"],
  "confidence": 0.0-1.0,
  "rationale": "分类理由",
  "requiresRepository": true|false,
  "complexity": "LOW|MEDIUM|HIGH"
}
```

### 2.3 任务规划升级

#### LlmTaskPlanner

```java
public class LlmTaskPlanner {

    public PlannedTasks plan(String userMessage,
                            ConversationContext context,
                            IntentAnalysisResult intent,
                            int maxTasks) {
        // 构建规划Prompt
        String prompt = buildPlanningPrompt(userMessage, context, intent, maxTasks);

        // 调用LLM
        String response = textGenerator.generate("TaskPlanner", "", prompt, context);

        // 解析响应
        return parseResponse(response);
    }
}
```

#### Prompt

```
你是一个代码审查任务规划专家。分析用户请求，分解为可执行的子任务。

用户请求: {userMessage}
意图分析: {intent}
仓库上下文: {repoInfo}

约束:
- 最多{maxTasks}个任务
- 每个任务必须包含: id, intentType, title, description, agentId, priority, dependencies
- intentType枚举: CODE_REVIEW, SECURITY_ANALYSIS, PERFORMANCE_ANALYSIS, ARCHITECTURE_ANALYSIS, DOCUMENTATION_GENERATION, KNOWLEDGE_RETRIEVAL
- agentId枚举: review-specialist, security-specialist, performance-specialist, architecture-specialist, documentation-specialist, rag-specialist
- priority: 1(高优先级)-5(低优先级)
- dependencies: 依赖的任务ID列表，无依赖则空列表

返回JSON:
{
  "tasks": [
    {
      "id": "task-1",
      "intentType": "CODE_REVIEW",
      "title": "执行代码审查",
      "description": "对仓库代码进行结构化审查",
      "agentId": "review-specialist",
      "priority": 1,
      "dependencies": []
    }
  ],
  "executionStrategy": "PARALLEL|SEQUENTIAL|MIXED",
  "rationale": "任务规划和执行策略的理由"
}
```

#### PlannedTasks 数据结构

```java
public record PlannedTasks(
    List<PlannedTask> tasks,
    ExecutionStrategy strategy,
    String rationale
) {}

public record PlannedTask(
    String id,
    IntentType intentType,
    String title,
    String description,
    String agentId,
    int priority,
    List<String> dependencies
) {}

public enum ExecutionStrategy {
    PARALLEL,   // 无依赖任务并行
    SEQUENTIAL, // 按依赖顺序串行
    MIXED       // 部分并行+部分串行
}
```

### 2.4 反思机制升级

#### LlmReflectionEvaluator

```java
public class LlmReflectionEvaluator implements ReflectionEvaluator {

    @Override
    public ReflectionEvaluation evaluate(ReflectionContext context) {
        // 构建评估Prompt
        String prompt = buildEvaluationPrompt(context);

        // 调用LLM
        String response = textGenerator.generate(
            "ReflectionEvaluator",
            "",
            prompt,
            Map.of("disableToolCallbacks", true)
        );

        // 解析响应
        return parseResponse(response);
    }
}
```

#### Prompt

```
你是代码审查反思评估专家。评估Agent的执行结果是否满足质量要求。

任务信息:
- 任务类型: {task.intentType}
- 任务描述: {task.description}
- 执行深度: {task.depth}

执行结果:
{executionResult}

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
```

#### ReflectionEvaluation 数据结构

```java
public record ReflectionEvaluation(
    boolean satisfied,
    double score,
    String gapDescription,
    List<String> improvementSuggestions
) {}
```

---

## 3. FlowAgent适配

### 3.1 执行策略处理

```java
public class FlowAgent {

    public FlowResult execute(...) {
        // ...

        // LLM规划的任务执行
        if (useLlmPlanning) {
            PlannedTasks planned = llmTaskPlanner.plan(userMessage, context, intent, MAX_PLANNED_TASKS);

            // 根据执行策略执行
            switch (planned.strategy()) {
                case PARALLEL -> runParallelSpecialists(planned.tasks(), ...);
                case SEQUENTIAL -> runSequentialTasks(planned.tasks(), ...);
                case MIXED -> runMixedTasks(planned.tasks(), ...);
            }
        } else {
            // 向后兼容: 简单请求用模板
            List<SubTask> tasks = buildInitialTasks(intent);
            // ...
        }
    }
}
```

### 3.2 依赖解析

```java
private List<List<SubTask>> buildExecutionLayers(List<PlannedTask> tasks) {
    // 1. 按优先级排序
    // 2. 构建依赖图
    // 3. 拓扑排序分层
    // 4. 无依赖的任务放同一层可并行

    Map<String, List<PlannedTask>> layers = new LinkedHashMap<>();
    // layer 0: 无依赖的任务
    // layer 1: 依赖layer 0的任务
    // ...
    return layers.values().stream().toList();
}
```

---

## 4. 配置项

```yaml
agent:
  planning:
    # 是否启用LLM规划
    enable-llm-planning: true
    # 复杂度阈值，超过则使用LLM规划
    complexity-threshold: 40
    # 最大任务数
    max-tasks: 6

  reflection:
    # 反思评估器类型: keyword | llm
    evaluator-type: llm
    # 最大反思深度
    max-reflection-depth: 3
```

---

## 5. 向后兼容

### 5.1 配置切换

```java
// SpecialistAgent
if ("keyword".equals(reflectionEvaluatorType)) {
    this.reflectionEvaluator = new KeywordReflectionEvaluator();
} else {
    this.reflectionEvaluator = new LlmReflectionEvaluator(textGenerator);
}

// IntentTaskPlanner
if (!enableLlmPlanning || complexityScore < complexityThreshold) {
    return buildInitialTasks(intent); // 快速模板
}
```

### 5.2 降级策略

- LLM调用失败 → 回退到快速模板/关键词评估
- 超时 → 回退到同步快速路径

---

## 6. 新增文件清单

| 文件 | 说明 |
|------|------|
| `agent/planning/LlmIntentClassifier.java` | LLM意图分类器 |
| `agent/planning/LlmTaskPlanner.java` | LLM任务规划器 |
| `agent/planning/PlannedTasks.java` | 规划结果数据结构 |
| `agent/planning/PlannedTask.java` | 任务数据结构 |
| `agent/planning/ExecutionStrategy.java` | 执行策略枚举 |
| `agent/reflection/LlmReflectionEvaluator.java` | LLM反思评估器 |

---

## 7. 修改文件清单

| 文件 | 改动 |
|------|------|
| `agent/planning/IntentTaskPlanner.java` | 集成LLM分类和规划 |
| `agent/SpecialistAgent.java` | 支持切换反思评估器 |
| `agent/FlowAgent.java` | 支持LLM规划结果执行 |
| `agent/AgentTextGenerator.java` | 新增generate方法支持 |
| `application.yml` | 新增配置项 |

---

## 8. 测试策略

### 8.1 单元测试
- `LlmIntentClassifierTest`: 测试各种意图场景
- `LlmTaskPlannerTest`: 测试任务分解逻辑
- `LlmReflectionEvaluatorTest`: 测试评估质量

### 8.2 集成测试
- 端到端测试: 简单请求 → 模板路径
- 端到端测试: 复杂请求 → LLM规划路径
- 回退测试: LLM失败 → 降级到模板

---

## 9. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| LLM规划质量不稳定 | Prompt优化 + 结果校验 + 降级 |
| LLM调用延迟 | 简单请求跳过LLM + 缓存 |
| JSON解析失败 | 降级到默认任务 + 日志告警 |
