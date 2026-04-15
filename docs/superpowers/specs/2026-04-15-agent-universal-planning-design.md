# Agent 自主规划与通用性增强设计方案

**文档版本**: v1.0  
**日期**: 2026-04-15  
**状态**: 待评审

---

## 1. 背景与目标

### 1.1 当前问题

现有 `code-review-*` 项目存在以下局限：

| 问题 | 描述 |
|------|------|
| **任务路由僵硬** | 依赖关键词匹配（SIMPLE_ANSWER / SINGLE_AGENT / MULTI_AGENT），无法处理复杂意图 |
| **任务拆解固定** | FlowAgent 串行执行 ReviewAgent → AdvisorAgent → RefactorAgent → SummarizerAgent，无灵活性 |
| **通用性不足** | 专注代码审查，无法处理编程问题解答、架构设计、文档生成等任务 |
| **自主规划缺失** | 缺乏类似 YuManus 的自主规划能力 |

### 1.2 改造目标

| 目标 | 描述 |
|------|------|
| **通用编程助手** | 支持：代码审查、编程问题解答、项目分析、文档生成 |
| **AI 自主规划** | 用户给高层意图，Agent 自动拆解任务、选择工具、动态协作 |
| **保留专业能力** | 现有代码审查 Chain 作为 SpecialistAgent 集成到新架构 |

---

## 2. 设计决策

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 自主规划程度 | **B - 中等** | 动态组合子任务，按需调用专业Agent，支持动态RAG |
| 多Agent协作 | **B - 动态协作** | Agent间可相互调用，子任务完成可触发新子任务 |
| 代码审查保留 | **B - SpecialistAgent** | ReviewChain 转为 ReviewSpecialistAgent |
| 协作决策机制 | **A - 中央Coordinator** | PlannerAgent 统一调度，简单清晰 |

---

## 3. 架构设计

### 3.1 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                        用户请求                               │
│          (代码审查 / 编程问题 / 架构设计 / 文档生成)           │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     PlannerAgent                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ • 意图理解 (Intent Understanding)                        ││
│  │ • 任务拆解 (Task Decomposition)                          ││
│  │ • 子任务分配 (Task Allocation)                           ││
│  │ • 结果汇总 (Result Aggregation)                          ││
│  └─────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────┐
│ ReviewSpecialist│ │ Architecture    │ │ Documentation      │
│ Agent           │ │ Specialist      │ │ Specialist         │
│ (现有Chain改造)  │ │ Agent           │ │ Agent              │
└────────┬────────┘ └────────┬────────┘ └──────────┬─────────┘
         │                   │                      │
         │   ┌───────────────┼───────────────┐      │
         │   ▼               ▼               ▼      │
         │ ┌──────────┐ ┌──────────┐ ┌───────────┐ │
         │ │Syntax    │ │Security  │ │Performance│ │
         │ │Check     │ │Scan      │ │Node       │ │
         │ │Node      │ │Node      │ │(复用)     │ │
         │ └──────────┘ └──────────┘ └───────────┘ │
         │                                          │
         └──────────────┬───────────────────────────┘
                        ▼
┌──────────────────────────────────────────────────────────────┐
│                    Shared Tool Pool                           │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌───────────┐ │
│  │ GitDiff    │ │ SonarQube  │ │ RAG检索    │ │ FileOp    │ │
│  │ (复用)     │ │ (复用)     │ │ (增强)     │ │ (新增)    │ │
│  └────────────┘ └────────────┘ └────────────┘ └───────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### 3.2.1 PlannerAgent（合并到 FlowAgent）

**决策**：PlannerAgent 合并到现有 FlowAgent，不新增独立模块

**职责**：
- 接收用户高层意图
- 调用 LLM 分析意图类型
- 拆解为可执行的子任务
- 分配给合适的 SpecialistAgent
- 收集结果并汇总

**核心流程**：

```java
public class PlannerAgent {
    // 1. 意图理解
    public IntentAnalysisResult analyzeIntent(String userMessage);
    
    // 2. 任务拆解
    public List<SubTask> decomposeTask(IntentAnalysisResult intent);
    
    // 3. 任务执行
    public TaskExecutionResult executeTask(SubTask task, List<SpecialistAgent> agents);
    
    // 4. 结果汇总
    public String aggregateResults(List<TaskExecutionResult> results);
}
```

**意图分类**：

| Intent | 路由到 |
|--------|--------|
| 代码审查 | ReviewSpecialistAgent |
| 性能优化 | PerformanceSpecialistAgent |
| 安全问题 | SecuritySpecialistAgent |
| 架构设计 | ArchitectureSpecialistAgent |
| 文档生成 | DocumentationSpecialistAgent |
| 编程问题 | GeneralCodingAgent |

#### 3.2.2 SpecialistAgent 体系

**基类设计**：

```java
public abstract class SpecialistAgent {
    String agentId;
    String specialty;           // 专业领域
    List<String> canHandle;    // 能处理的意图类型
    
    public abstract boolean canHandle(IntentAnalysisResult intent);
    public abstract String execute(SubTask task, Context context);
    public abstract List<SubTask> proposeSubTasks(String result);  // 发现新子任务
}
```

**专业Agent列表**：

| Agent | 职责 | 复用/新建 |
|-------|------|----------|
| ReviewSpecialistAgent | 代码审查 | 复用 ReviewChain |
| SecuritySpecialistAgent | 安全分析 | 复用现有 |
| PerformanceSpecialistAgent | 性能分析 | 复用现有 |
| ArchitectureSpecialistAgent | 架构设计与分析 | **新建** |
| DocumentationSpecialistAgent | 文档生成与总结 | **新建** |
| GeneralCodingAgent | 通用编程问题 | **新建** |

#### 3.2.3 动态协作机制

**子任务触发链**：

```java
public class DynamicCollaboration {
    // Agent 执行完成后，可提议新子任务
    List<SubTask> proposeSubTasks(String executionResult);
    
    // Planner 决定是否采纳提议
    boolean acceptProposal(SubTask proposedTask);
    
    // 触发新任务执行
    void triggerSubTask(SubTask task);
}
```

**协作模式**：

| 场景 | 协作方式 |
|------|----------|
| 代码审查发现安全问题 | ReviewAgent → Planner → SecuritySpecialistAgent |
| 架构分析需要性能评估 | ArchitectureAgent → Planner → PerformanceSpecialistAgent |
| 文档生成需要代码解释 | DocAgent → Planner → ReviewAgent（获取代码片段）|

**子任务触发流程**：
```
SpecialistAgent 完成执行 
    → proposeSubTasks() 提议新子任务 
    → 发送给 PlannerAgent 确认 
    → Planner 决定是否采纳 
    → 接受则分配执行，拒绝则忽略
```

---

### 3.2.4 自我反思机制 (Self-Reflection)

**设计**：每个 SpecialistAgent 执行完成后，进行结果质量评估。

```java
public interface SelfReflection {
    // 评估执行结果是否满足意图
    ReflectionResult reflect(IntentAnalysisResult originalIntent, 
                           SubTask task, 
                           String executionResult);
    
    // 反思结果
    boolean isSatisfied();      // 是否满足
    String getGap();            // 差距描述
    List<SubTask> getRemediation(); // 弥补措施
}
```

**反思流程**：
```
Agent 执行完成 
    → SelfReflection.reflect() 
    → 评估结果质量
        ├── 满足 → 继续
        ├── 不满足 → 提出弥补子任务 → Planner确认 → 执行弥补
```

**反思维度**：

| 维度 | 检查点 |
|------|--------|
| 完整性 | 是否覆盖了用户意图的所有方面？ |
| 准确性 | 结论是否有证据支撑？ |
| 可行性 | 建议是否可执行？ |
| 上下文 | 是否考虑了项目背景和规范？ |

---

### 3.2.5 失败回溯机制 (Failure Recovery / Backtracking)

**设计**：当任务执行失败或规划不可行时，记录失败路径并回退。

```java
public class ExecutionHistory {
    List<ExecutionNode> executedPath;  // 已执行路径
    Set<String> failedPaths;           // 失败路径记录
    
    void recordSuccess(SubTask task, String result);
    void recordFailure(SubTask task, String reason);
    boolean isPathFailed(List<SubTask> path);
}

public class BacktrackingPlanner {
    // 回退到检查点
    ExecutionNode checkpoint();
    
    // 重试替代路径
    List<SubTask> findAlternativePath(SubTask failedTask);
    
    // 最大回退次数
    int maxRetries = 3;
}
```

**回溯流程**：
```
任务执行失败
    → 记录到 ExecutionHistory
    → 检查是否还有替代路径
        ├── 有 → 回退到检查点，选择替代路径
        ├── 无 → 报告失败，返回已收集结果
    → 最大回退3次
```

**失败检测**：

| 失败类型 | 检测方式 | 处理 |
|----------|----------|------|
| 工具执行失败 | 异常捕获 | 重试3次，失败则回溯 |
| LLM规划失败 | 空结果/无效结果 | 切换规则fallback |
| 死循环 | 协作深度超限 | 强制终止 |
| 意图无法满足 | 反思机制 | 承认局限，返回部分结果 |

---

### 3.2.6 增强后的完整流程

```
用户高层意图
    │
    ▼
┌─────────────────────────────┐
│   Planner (FlowAgent)       │
│   1. 意图理解               │
│   2. 任务拆解               │
│   3. 检查ExecutionHistory   │  ← 回溯：跳过失败路径
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│   SpecialistAgent 执行       │
│   1. ReAct循环执行           │
│   2. 自我反思评估            │  ← 反思：不满足则弥补
│   3. 提议新子任务            │
└─────────────────────────────┘
    │
    ├── 成功 → 返回结果
    │
    └── 失败/不满足
            ├── 回退到Planner
            ├── 记录失败路径
            ├── 尝试替代路径
            └── 重复直到成功或耗尽
```

---

## 4. 新增能力设计

### 4.1 ArchitectureSpecialistAgent

**职责**：
- 分析代码结构
- 评估架构合理性
- 提供设计建议
- 生成架构图（PlantUML/Mermaid）

**工具**：
- `file_analyzer` - 分析项目结构
- `dependency_graphper` - 生成依赖图
- `architecture_pattern_detector` - 检测架构模式

### 4.2 DocumentationSpecialistAgent

**职责**：
- 代码注释生成
- API 文档生成
- README 生成
- 技术文档撰写

**工具**：
- `code_documenter` - 生成代码文档
- `readme_generator` - 生成 README
- `api_doc_generator` - 生成 API 文档

### 4.3 GeneralCodingAgent

**职责**：
- 编程问题解答
- 代码调试
- 算法解释
- 技术选型建议

**工具**：
- `search_documentation` - 搜索官方文档
- `search_stackoverflow` - 搜索 StackOverflow
- `code_executor` - 执行代码（沙箱）

---

## 5. 工具层增强

### 5.1 工具池

| 工具 | 来源 | 说明 |
|------|------|------|
| git_diff_fetch | 复用 | Git 操作 |
| sonar_scan | 复用 | SonarQube 扫描 |
| review_history_search | 复用 | 历史审查检索 |
| session_memory_read | 复用 | 会话记忆 |
| chat_history_search | 复用 | 聊天历史 |
| norm_search | 复用 | 规范检索 |
| file_operation | **新建** | 文件读写 |
| web_search | **新建** | 网页搜索 |
| code_search | **新建** | 代码搜索 |

### 5.2 RAG 增强

**现有 RAG**：
- Milvus 向量存储
- 历史审查记录检索
- PDF 规范文档检索

**增强为**：
- 官方文档检索
- StackOverflow 知识检索
- 项目内部知识检索

---

## 6. 消息传递机制

### 6.1 Agent 间通信

```java
public interface AgentMessageBus {
    // 发布消息
    void publish(String topic, AgentMessage message);
    
    // 订阅消息
    void subscribe(String topic, AgentCallback callback);
    
    // 请求-响应模式
    CompletableFuture<Message> request(String agentId, Message message);
}
```

### 6.2 消息类型

| MessageType | 说明 |
|-------------|------|
| TASK_ASSIGNMENT | 任务分配 |
| TASK_RESULT | 任务结果 |
| SUBTASK_PROPOSAL | 子任务提议 |
| SUBTASK_ACCEPTED | 子任务接受 |
| COLLABORATION_REQUEST | 协作请求 |
| TERMINATE | 终止信号 |

---

## 7. 实施计划

### Phase 1: 架构重构

| 任务 | 说明 |
|------|------|
| 设计 PlannerAgent 接口 | 中央调度器（FlowAgent升级）|
| 抽象 SpecialistAgent 基类 | 统一子Agent规范 |
| 迁移现有 Agent | Review/Security/Performance → SpecialistAgent |

### Phase 2: 新增 Agent

**优先级**：三者同等优先级，由 LLM 根据意图自主选择

| 任务 | 说明 |
|------|------|
| 实现 ArchitectureSpecialistAgent | 架构分析（代码结构、设计模式、依赖关系）|
| 实现 DocumentationSpecialistAgent | 文档生成（代码注释、API文档、README）|
| 实现 GeneralCodingAgent | 通用编程（问题解答、调试、算法解释）|

### Phase 3: 动态协作

| 任务 | 说明 |
|------|------|
| 实现 AgentMessageBus | 消息传递（增强）|
| 实现 proposeSubTasks 机制 | 子任务发现 |
| 实现结果汇总 | PlannerAgent 汇总逻辑 |

### Phase 4: 自我反思与失败回溯

| 任务 | 说明 |
|------|------|
| 实现 SelfReflection 接口 | 反思评估机制 |
| 实现 ExecutionHistory | 执行历史与失败路径记录 |
| 实现 BacktrackingPlanner | 回退到检查点、寻找替代路径 |
| 集成反思到 SpecialistAgent | Agent执行后自动反思 |
| 集成回溯到 PlannerAgent | 失败时自动回溯重试 |

### Phase 5: 工具增强

| 任务 | 说明 |
|------|------|
| 新增 file_operation 工具 | 文件操作 |
| 新增 web_search 工具 | 网页搜索 |
| 增强 RAG 检索能力 | 扩展知识库 |

---

## 8. 风险与挑战

### 8.1 新增能力的残余风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 反思质量依赖LLM | 中 | 设置反思模板，引导评估维度 |
| 回溯可能陷入循环 | 高 | 最大回退3次，记录已尝试路径 |
| 替代路径耗尽 | 中 | 承认局限，返回部分结果 |
| 性能开销增加 | 低 | 简单任务跳过反思/回溯 |

### 8.2 总体能力评估

| 能力 | 改造前 | 改造后 |
|------|--------|--------|
| 任务拆解 | 固定Pipeline | LLM自主拆解 |
| 意图理解 | 关键词匹配 | LLM理解 |
| 动态协作 | 无 | 有（Planner确认）|
| 自我反思 | 无 | 有 |
| 失败回溯 | 无 | 有 |
| **自主程度** | **50%** | **85%** |

### 8.3 与理想Agent的差距

| 能力 | 理想Agent | 当前方案 | 差距 |
|------|-----------|----------|------|
| 自我反思 | ✅ | ✅ | - |
| 失败回溯 | ✅ | ✅ | - |
| 工具生成 | ✅ | ❌ | 无法动态生成新工具 |
| 无限重试 | ✅ | ❌ | 受限于最大回退次数 |
| 代码执行验证 | ✅ | ❌ | 沙箱能力弱 |
| **最终自主度** | **95%** | **85%** | **差距10%** |

---

## 9. 附录

### 9.1 与 yu-ai-agent-master 对比

| 维度 | 本方案 | yu-ai-agent-master |
|------|--------|-------------------|
| 架构 | PlannerAgent（合并到FlowAgent）+ SpecialistAgent | ToolCallAgent → YuManus |
| 规划 | 中央调度（FlowAgent统一入口）| 单一Agent自主规划 |
| 专业性 | 垂直领域深耕 | 通用但不深入 |
| 协作 | 动态协作（Planner确认子任务）| 单一Agent |

### 9.2 核心文件变更

| 文件 | 变更 |
|------|------|
| `code-review-core/.../agent/FlowAgent.java` | 升级为 PlannerAgent，扩展意图理解、任务拆解、回溯能力 |
| `code-review-core/.../agent/SpecialistAgent.java` | 新增基类，迁移现有Agent，集成自我反思 |
| `code-review-core/.../agent/SelfReflection.java` | **新增**，反思评估接口 |
| `code-review-core/.../agent/ExecutionHistory.java` | **新增**，执行历史与失败路径记录 |
| `code-review-core/.../agent/BacktrackingPlanner.java` | **新增**，回溯重试逻辑 |
| `code-review-core/.../coordination/AgentMessageBus.java` | 增强支持子任务提议和确认机制 |
| `code-review-tools/` | 新增 Architecture/Doc/GeneralCoding 工具 |
| `code-review-api/` | 新增 Architecture/Doc API（如需要外部调用）|

---

**已确认内容**：
1. ✅ PlannerAgent 合并到 FlowAgent
2. ✅ Phase 2 三个新Agent同等优先级，LLM自主选择
3. ✅ 子任务触发需 Planner 确认
