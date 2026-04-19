# AI Code Review Agent

智能代码审查与重构助手 - 基于大语言模型的自动化代码审查系统

## 项目简介

AI Code Review Agent 是一款基于 **ReAct (Reasoning + Acting)** 架构的智能代码审查系统。系统通过多Agent协作、混合RAG检索、动态工具调用等技术，实现对代码的深度分析、安全漏洞检测、性能优化建议和规范合规性检查。

## 功能说明

### 能干什么？

| 功能分类 | 具体能力 |
|----------|----------|
| **代码审查** | 语法检查、代码风格审查、安全扫描、性能分析 |
| **智能对话** | 快速问答、深度分析、多轮对话、上下文记忆 |
| **知识检索** | 历史审查记录检索、规范文档检索、聊天历史检索 |
| **代码处理** | 代码差异获取、重构建议生成、代码优化 |

### 能做什么？

#### 1. 对话式代码审查/本地代码审查

**场景**：用户通过自然语言与AI对话，获得代码审查建议

![image-20260419175404010](https://gitee.com/Wsj123789/wsj/raw/master/img/20260419175405323.png)

```
用户: "帮我检查这段代码有没有安全问题"
AI:   检测到安全相关，启动SecuritySpecialistAgent
      ↓
      AI思考: 需要调用sonar_scan工具扫描
      ↓
      执行sonar_scan，获取扫描结果
      ↓
      AI分析扫描结果，输出安全报告
```

**支持的对话模式**：
- **快速问答** (`/api/chat/send`): 简单问题即时回答，延迟<1秒
- **深度分析** (`/api/chat/react`): SSE流式输出，展示AI思考全过程

![image-20260419175642981](https://gitee.com/Wsj123789/wsj/raw/master/img/20260419175644064.png)

#### 2. 提交式代码审查

**场景**：提交Git仓库或代码片段，获得完整审查报告

![image-20260419175254761](https://gitee.com/Wsj123789/wsj/raw/master/img/20260419175303628.png)

**两种提交方式**：

| 方式 | 说明 | API |
|------|------|-----|
| GIT_DIFF | 传入仓库URL，自动拉取Diff | `POST /api/review/submit` |
| PASTE_CODE | 直接粘贴代码片段 | `POST /api/review/submit` |

**审查流程**：

```
提交审查 → 语法检查 → 风格检查 → 安全扫描 → 性能分析 → 生成报告
```

#### 3. 批量审查

**场景**：一次性审查多个文件或多个提交

```http
POST /api/review/batch
{
  "repoUrl": "https://github.com/xxx/yyy",
  "commits": ["commit1", "commit2", "commit3"],
  "projectId": "project-001"
}
```

#### 4. 增量审查

**场景**：只审查新增或修改的代码

```http
POST /api/review/incremental
{
  "repoUrl": "https://github.com/xxx/yyy",
  "baseBranch": "main",
  "headBranch": "feature-xxx",
  "projectId": "project-001"
}
```

#### 5. 规范管理

**上传PDF规范文档**，AI根据规范进行审查

```
上传PDF → 文本提取 → 分块处理 → 向量化存储 → 检索时匹配
```

**支持的规范检索**：
- 项目编码规范
- 安全合规要求
- 架构设计规范
- 第三方库使用规范

#### 6. 知识库问答

基于历史审查记录和规范文档的智能问答

```
用户: "我们项目的SQL注入规范是什么？"
AI:   检索norm_search工具 → 匹配相关规范文档 → 返回规范摘要
```

### 达到什么效果？

#### 1. 审查报告示例

```json
{
  "reviewId": "review-xxx",
  "score": 76,
  "totalIssues": 5,
  "issues": [
    {
      "severity": "CRITICAL",
      "file": "UserService.java",
      "lineNumber": 42,
      "message": "疑似硬编码密钥/密码，请立即移除敏感信息",
      "ruleId": "SEC_HARDCODED_SECRET",
      "suggestion": "使用环境变量或密钥管理服务替代硬编码"
    },
    {
      "severity": "HIGH", 
      "file": "UserService.java",
      "lineNumber": 68,
      "message": "检测到高风险动态执行逻辑，存在注入风险",
      "ruleId": "SEC_COMMAND_INJECTION",
      "suggestion": "增加白名单校验并避免直接执行外部输入"
    }
  ],
  "summary": "共发现5个问题，请优先处理高危与严重项"
}
```

#### 2. 评分机制

| 评分范围 | 等级 | 说明 |
|----------|------|------|
| 90-100 | 优秀 | 代码质量良好，仅有轻微建议 |
| 70-89 | 良好 | 有少量低风险问题 |
| 50-69 | 一般 | 存在中等风险问题，需关注 |
| 30-49 | 较差 | 存在高风险问题，建议修复 |
| 0-29 | 危险 | 存在严重问题，必须修复 |

**扣分规则**：
- CRITICAL: -20分
- HIGH: -12分
- MEDIUM: -6分
- LOW: -3分

#### 3. 实时流式输出效果

前端SSE接收展示效果：

```
[AI正在分析] ████████████░░░░░░░ 60%

执行节点：Security Specialist
Agent 开始：Security Specialist

[思考过程]
Round 1/8: 分析安全问题涉及的关键点
  └  检测到代码中包含敏感信息操作

[工具调用] ████████████████░░░░ 80%
  ├─ tool_call: sonar_scan
  │   └  input: projectKey=xxx, branch=main
  ├─ tool_result: 发现1个CRITICAL安全问题

[结论]
  └  发现1个CRITICAL问题，需要立即处理
```

#### 4. 核心效果指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| **响应时间** | 快速问答<1s，深度分析<30s | 视代码复杂度和工具调用次数 |
| **问题发现率** | 覆盖80%+常见问题 | 包括安全、性能、风格等 |
| **误报率** | <15% | AI生成内容可能存在偏差 |
| **上下文记忆** | 跨会话保留历史 | 支持多轮追问 |
| **工具调用准确率** | >90% | AI正确选择工具的能力 |

### 典型使用场景

#### 场景1: PR代码审查

```
开发者提交PR → AI自动审查PR差异 → 输出审查报告
    ↓
    发现3个安全问题、2个性能问题、1个规范问题
    ↓
    评分: 68分 (一般)
    ↓
    建议: 优先修复SEC_HARDCODED_SECRET问题
```

#### 场景2: 对话式咨询

```
用户: "这个SQL查询为什么这么慢？"
AI:   检测到性能问题，启动PerformanceSpecialistAgent
    ↓
    调用sonar_scan获取性能数据
    ↓
    识别出N+1查询问题和缺失索引
    ↓
    给出具体的优化建议和重构代码
```

#### 场景3: 规范合规检查

```
上传项目规范PDF → 存储到知识库
    ↓
用户: "这段代码符合我们的规范吗？"  
AI:   检索norm_search → 匹配相关规范条款 → 检查合规性
    ↓
输出: "符合3条，有2条需要改进..."
```

## 核心特性

- **多Agent协作系统**：协调者自动分发任务到8种专业Agent（安全、性能、审查、知识库、文档、架构、通用编码、本地代码）
- **真ReAct迭代循环**：AI自主决策工具调用，动态收集证据直到形成结论，支持回溯规划
- **混合RAG检索**：语义向量 + BM25关键词融合排序(BM25权重1.15略优先)，提升检索准确性
- **多层记忆系统**：滑动窗口(20条/会话) + 代码上下文 + ChatHistoryRepository跨会话检索
- **MCP工具生态**：标准化工具注册中心，支持Git、Sonar、重构、文件操作、代码搜索、Web搜索
- **实时流式输出**：SSE推送AI思考过程，工具调用可见，支持WebSocket
- **自我反思机制**：SelfReflection + BacktrackingPlanner支持执行回溯
- **审查链管道**：SyntaxCheck → StyleCheck → SecurityScan → Performance 四阶段管道

## 技术架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         React + Ant Design 前端                          │
│              ReviewPage / useAgentStream (SSE实时通信)                     │
│              WebSocket / AgentWebSocketHandler                           │
└─────────────────────────────────────────────────────────────────────────┘
                                        │ SSE / HTTP / WebSocket
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot 后端                                  │
│  ┌──────────────────┐  ┌─────────────────────────────────────────────┐  │
│  │  ChatController  │  │              ConversationalCoordinator       │  │
│  │  ReviewController │  │         (意图分析 + Agent路由)               │  │
│  │  KnowledgeCtrl   │  └─────────────────┬───────────────────────────┘  │
│  │  HistoryCtrl     │                    │                               │
│  └──────────────────┘                    ▼                               │
│                               ┌───────────────────────────────────────┐   │
│                               │           FlowAgent                  │   │
│                               │        (Flow编排+执行)                │   │
│                               └─────────────────┬─────────────────────┘   │
│                                                     │                      │
│                        ┌────────────────────────────┼────────────────┐    │
│                        ▼                            ▼                ▼    │
│              ┌─────────────────┐    ┌─────────────────┐  ┌─────────────┐   │
│              │   ReActAgent     │    │  SimpleAgent   │  │ ReviewAgent │   │
│              │  (深度分析循环)    │    │   (快速回答)    │  │ (审查Agent)  │   │
│              └────────┬─────────┘    └─────────────────┘  └─────────────┘   │
│                       │                                                  │
│                       ▼                                                  │
│              ┌─────────────────────────────────────────┐                   │
│              │         ReactLoop (迭代循环)             │                   │
│              │    think() → decide() → act() → observe()│                   │
│              │    + BacktrackingPlanner (回溯规划)      │                   │
│              └────────────────────┬────────────────────┘                   │
│                                    │                                       │
│    ┌───────────────────────────────┼───────────────────────────────┐       │
│    │                               │                               │       │
│    ▼                               ▼                               ▼       │
│ ┌──────────────────┐  ┌──────────────────────┐  ┌──────────────────┐       │
│ │ SpecializedAgent │  │ SpecializedAgent    │  │ SpecializedAgent │       │
│ │ (Review)         │  │ (Security)          │  │ (Performance)     │       │
│ ├──────────────────┤  ├──────────────────────┤  ├──────────────────┤       │
│ │ SpecializedAgent │  │ SpecializedAgent    │  │ SpecializedAgent │       │
│ │ (Rag)            │  │ (Documentation)      │  │ (Architecture)    │       │
│ ├──────────────────┤  ├──────────────────────┤  ├──────────────────┤       │
│ │ SpecializedAgent │  │ SpecializedAgent    │  │ SpecializedAgent │       │
│ │ (GeneralCoding)  │  │ (LocalCode)          │  │                  │       │
│ └──────────────────┘  └──────────────────────┘  └──────────────────┘       │
│                                    │                                       │
│                                    ▼                                       │
│                         ┌──────────────────────┐                            │
│                         │    ToolRegistry     │                            │
│                         │   (MCP工具中心)      │                            │
│                         │  McpClient          │                            │
│                         └──────────────────────┘                            │
│  ┌───────────────────────┼───────────────────────────────┐                  │
│  │  ReviewChain          │     Memory                     │                  │
│  │  (管道式审查)          │  SlidingWindowMemory           │                  │
│  │  - SyntaxCheckNode   │  SessionCodeContextMemory      │                  │
│  │  - StyleCheckNode    │  ChatHistoryRepository          │                  │
│  │  - SecurityScanNode  │                                │                  │
│  │  - PerformanceNode   │                                │                  │
│  └───────────────────────┴────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        ▼                               ▼                               ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐
│     Milvus      │    │     MySQL       │    │     Redis       │    │   PDF存储   │
│   (向量数据库)   │    │   (结构化存储)   │    │   (会话缓存)    │    │   本地文件   │
│   - 审查历史    │    │   - 聊天记录    │    │   - 滑动窗口    │    │             │
│   - 规范文档    │    │   - 任务状态    │    │   - Embedding缓存│   │             │
│   - ChatHistory │    │   - 用户信息    │    │                 │    │             │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────┘
```

### 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **前端** | React 18 + TypeScript | 单页应用 |
| | Vite 7 | 构建工具 |
| | Ant Design 5 | UI组件库 |
| | SSE + WebSocket | 实时通信 |
| **后端** | Spring Boot 3.2 | 基础框架 |
| | Spring AI 1.1.0 | 大模型集成 |
| | spring-ai-alibaba-agent-framework 1.1.0.0 | 阿里Agent框架 |
| | MyBatis-Plus 3.5.7 | ORM框架 |
| | spring-ai-starter-vector-store-milvus | Milvus向量存储 |
| **存储** | Milvus 3.0 | 向量数据库 |
| | MySQL 8.0 | 关系数据库 |
| | Redis | 会话缓存 + Embedding缓存 |
| **PDF处理** | Apache PDFBox 3.0.2 | PDF文本提取 |
| | OpenPDF 1.3.30 | PDF生成导出 |
| **工具** | McpClient | 工具注册中心(统一工具调用) |
| | GitDiffFetcher | Git仓库Diff获取 |
| | SonarResultParser | Sonar扫描结果解析 |
| | CodeRefactorer | 代码重构建议 |
| | RetryTemplate | 重试模板(指数退避) |

## 项目结构

```
code-review-agent/
├── code-review-common/          # 通用模块
│   ├── model/                   # 数据模型
│   │   ├── auth/               # 认证相关模型
│   │   ├── chat/               # 对话相关模型
│   │   ├── history/            # 历史记录模型
│   │   ├── knowledge/          # 知识库模型
│   │   ├── norm/               # 规范相关模型
│   │   ├── review/             # 审查相关模型
│   │   ├── session/            # 会话模型
│   │   └── template/           # 审查模板模型
│   ├── persistence/            # 数据库层
│   │   ├── entity/             # DO实体
│   │   └── mapper/             # MyBatis Mapper
│   ├── utils/                  # 工具类 (IdUtils, HashUtils)
│   ├── result/                 # 统一响应 (ResultCode, ApiResponse)
│   └── exception/              # 异常类 (BizException)
│
├── code-review-tools/           # 工具模块
│   ├── mcp/                    # MCP协议实现
│   │   ├── McpClient.java             # MCP客户端(工具注册中心)
│   │   ├── McpToolDefinition.java      # 工具定义元数据
│   │   └── McpToolExecutor.java       # 工具执行器
│   ├── git/
│   │   └── GitDiffFetcher.java        # Git仓库Diff获取
│   ├── sonar/
│   │   └── SonarResultParser.java     # Sonar扫描结果解析
│   ├── refactor/
│   │   └── CodeRefactorer.java        # 代码重构建议生成
│   ├── retry/
│   │   ├── RetryTemplate.java         # 重试模板
│   │   ├── RetryStrategy.java         # 重试策略
│   │   └── Retryable.java            # 可重试标记
│   ├── file/
│   │   ├── FileOperationTool.java     # 文件操作工具
│   │   └── LocalFileOperationTool.java # 本地文件操作
│   ├── search/
│   │   ├── CodeSearchTool.java        # 代码搜索工具
│   │   └── WebSearchTool.java         # Web搜索工具
│   └── exception/
│       └── ToolExecutionException.java # 工具执行异常
│
├── code-review-rag/             # RAG检索模块
│   ├── retrieval/                      # 检索策略
│   │   ├── HybridSearch.java          # 混合检索(语义+BM25+RRF融合)
│   │   ├── ChatRetrieval.java         # 对话上下文检索
│   │   └── QueryRewriter.java         # Query重写(中文分词+扩展)
│   ├── vector/
│   │   ├── MilvusRepository.java      # Milvus向量库操作
│   │   └── CollectionManager.java      # Collection管理
│   ├── embedding/
│   │   ├── EmbeddingService.java      # 向量化服务
│   │   ├── TextChunker.java           # 文本分块(512字符重叠)
│   │   └── PdfExtractor.java          # PDF文本提取
│   ├── knowledge/
│   │   └── ReviewKnowledgeBase.java   # 审查知识库管理
│   ├── cache/
│   │   └── EmbeddingCache.java        # Embedding本地缓存(LRU)
│   ├── model/
│   │   └── ReviewRecord.java          # 审查记录模型
│   ├── ChatHistoryRepository.java     # 聊天历史存储
│   └── PdfRepository.java             # PDF文档存储
│
├── code-review-core/            # 核心Agent模块
│   ├── agent/                   # Agent实现
│   │   ├── conversational/      # 对话Agent
│   │   │   ├── ConversationalCoordinator.java  # 协调者(意图分析+路由)
│   │   │   ├── ReActAgent.java                  # 深度分析Agent(ReAct循环)
│   │   │   ├── SimpleAgent.java                  # 快速回答Agent
│   │   │   ├── ConversationContext.java          # 对话上下文
│   │   │   ├── CoordinationDecision.java        # 协调决策
│   │   │   ├── CoordinationType.java             # 协调类型(单Agent/多Agent协作)
│   │   │   ├── ReactStreamListener.java          # SSE事件监听
│   │   │   └── SpecialistReport.java             # 专家报告
│   │   ├── specialized/       # 专业Agent(继承SpecialistAgent)
│   │   │   ├── SpecialistAgent.java              # 专业Agent抽象基类
│   │   │   ├── ReviewSpecialistAgent.java       # 审查专家(代码Bug/风格)
│   │   │   ├── SecuritySpecialistAgent.java     # 安全专家(注入/敏感信息)
│   │   │   ├── PerformanceSpecialistAgent.java # 性能专家(复杂度/查询)
│   │   │   ├── RagSpecialistAgent.java         # 知识库专家(规范检索)
│   │   │   ├── DocumentationSpecialistAgent.java # 文档专家
│   │   │   ├── ArchitectureSpecialistAgent.java # 架构专家
│   │   │   ├── GeneralCodingAgent.java         # 通用编码Agent
│   │   │   └── LocalCodeSpecialistAgent.java   # 本地代码Agent
│   │   ├── react/             # ReAct循环
│   │   │   ├── ReactLoop.java           # 迭代循环(think→act→observe)
│   │   │   ├── ReactState.java         # 迭代状态
│   │   │   ├── ReactDecision.java      # 决策结果(工具调用/终止)
│   │   │   ├── ReactContext.java       # 执行上下文
│   │   │   ├── ReactResult.java        # 执行结果
│   │   │   ├── ToolCallResult.java     # 工具调用结果
│   │   │   └── ThinkingStep.java       # 思考步骤
│   │   ├── planning/          # 规划组件
│   │   │   ├── IntentType.java         # 意图类型
│   │   │   ├── IntentAnalysisResult.java # 意图分析结果
│   │   │   ├── TaskExecutionResult.java # 任务执行结果
│   │   │   ├── SubTask.java            # 子任务
│   │   │   ├── BacktrackingPlanner.java # 回溯规划器
│   │   │   ├── ExecutionHistory.java   # 执行历史
│   │   │   ├── ExecutionNode.java       # 执行节点
│   │   │   └── ReflectionResult.java   # 反思结果
│   │   ├── BaseAgent.java              # Agent基类
│   │   ├── Agent.java                   # Agent接口
│   │   ├── AgentTextGenerator.java     # 文本生成器接口
│   │   ├── LocalAgentTextGenerator.java # 本地文本生成
│   │   ├── FlowAgent.java              # Flow编排Agent
│   │   ├── FlowResult.java             # Flow执行结果
│   │   ├── ReviewAgent.java            # 审查Agent
│   │   ├── AdvisorAgent.java           # 顾问Agent
│   │   ├── RefactorAgent.java          # 重构Agent
│   │   ├── SummarizerAgent.java        # 总结Agent
│   │   ├── SelfReflection.java         # 自我反思
│   │   ├── SpecialistAgent.java        # SpecialistAgent(spring管理)
│   │   ├── AgentExecutionContext.java  # Agent执行上下文
│   │   └── AgentEventListener.java     # Agent事件监听
│   ├── memory/                # 记忆管理
│   │   ├── ChatMemory.java              # 聊天记忆接口
│   │   ├── SlidingWindowMemory.java     # 滑动窗口实现(20条/会话)
│   │   └── SessionCodeContextMemory.java # 代码上下文记忆
│   ├── chain/                 # 审查链(管道式处理)
│   │   ├── ReviewChain.java            # 审查管道
│   │   ├── ReviewContext.java          # 审查上下文
│   │   ├── Node.java                    # 节点接口
│   │   ├── NodeResult.java             # 节点执行结果
│   │   └── nodes/
│   │       ├── SyntaxCheckNode.java    # 语法检查节点
│   │       ├── StyleCheckNode.java     # 风格检查节点
│   │       ├── SecurityScanNode.java   # 安全扫描节点
│   │       └── PerformanceNode.java   # 性能分析节点
│   ├── coordination/          # 协调组件
│   │   ├── MessageBus.java             # 消息总线
│   │   ├── MessageType.java            # 消息类型
│   │   ├── AgentMessage.java          # Agent消息
│   │   ├── AgentMessageBus.java       # Agent消息总线
│   │   ├── AgentRegistry.java         # Agent注册中心
│   │   └── InputOutputRouter.java     # 输入输出路由
│   ├── enhance/
│   │   └── CodeSimilarityDetector.java # 代码相似度检测
│   └── cache/
│       ├── ReviewResultCache.java      # 审查结果缓存
│       └── AgentResultMemo.java        # Agent结果备忘录
│
├── code-review-api/           # API模块
│   ├── controller/
│   │   ├── ChatController.java         # 对话接口(/api/chat/*)
│   │   ├── ReviewController.java       # 审查接口(/api/review/*)
│   │   ├── KnowledgeController.java    # 知识库接口
│   │   ├── HistoryController.java     # 历史记录接口
│   │   ├── LocalCodeController.java    # 本地代码审查接口
│   │   ├── SseController.java          # SSE推送接口
│   │   ├── UserController.java         # 用户接口
│   │   ├── AuthController.java         # 认证接口
│   ├── service/
│   │   ├── ChatService.java           # 基础聊天服务
│   │   ├── ConversationalService.java # 对话服务(深度分析)
│   │   ├── ReviewService.java         # 审查服务
│   │   ├── KnowledgeService.java      # 知识库服务
│   │   ├── KnowledgeManageService.java # 知识管理服务
│   │   ├── LocalCodeAnalysisService.java # 本地代码分析服务
│   │   ├── ReviewEnhancementService.java # 审查增强服务
│   │   ├── ReviewTemplateService.java # 审查模板服务
│   │   ├── PdfExportService.java     # PDF导出服务
│   │   ├── AuthService.java          # 认证服务
│   │   └── UserService.java          # 用户服务
│   ├── persistence/
│   │   ├── ReviewTaskPersistenceRepository.java # 审查任务持久化
│   │   └── MybatisPlusChatMemory.java # MyBatis聊天记忆
│   ├── config/
│   │   ├── ToolRegistrationConfig.java # MCP工具注册配置
│   │   ├── MilvusConnectionConfig.java # Milvus连接配置
│   │   ├── MilvusVectorStoreConfig.java # Milvus向量存储配置
│   │   ├── AiClientTimeoutConfig.java  # AI客户端超时配置
│   │   ├── AsyncConfig.java           # 异步配置
│   │   ├── SecurityConfig.java        # 安全配置
│   │   └── WebSocketConfig.java       # WebSocket配置
│   ├── websocket/
│   │   └── AgentWebSocketHandler.java # WebSocket处理器
│   ├── sse/
│   │   └── SseEmitterManager.java     # SSE管理器
│   ├── ai/
│   │   └── SpringAiAlibabaAgentTextGenerator.java # 阿里AI文本生成
│   ├── exception/
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   └── persistence/
│       └── *.java                      # 数据持久化相关
│
├── code-review-start/         # 启动模块
│   └── CodeReviewApplication.java    # Spring Boot启动类
│
└── front/                     # 前端(React)
    └── src/
        ├── pages/Review/             # 审查页面
        ├── components/               # 组件
        ├── hooks/useAgentStream.ts   # SSE流式Hook
        └── services/api.ts           # API调用
```

## 核心模块详解

### 1. ReAct Agent 架构

#### 迭代循环 (ReactLoop)

```java
// 核心循环：think → act → observe，直到停机
while (state.iteration() < MAX_ITERATIONS) {
    // 1. THINK: LLM决定下一步
    ReactDecision decision = agent.decideNextAction(userMessage, context, state);
    
    if (decision.action() != Action.TOOL) {
        // 无工具调用，生成最终结论
        break;
    }
    
    // 2. ACT: 执行工具
    ToolCallResult result = agent.callTool(decision.toolName(), decision.toolParams(), ...);
    
    // 3. OBSERVE: 更新状态
    state = state.observe(result);
    
    // 4. 停机检测
    if (agent.shouldTerminate(state)) break;
}
```

#### 动态工具决策

```java
// LLM自主选择工具
private ReactDecision planWithModel(...) {
    // 构建prompt，包含所有可用工具描述
    String input = "[Available Tools]\n" + formatToolDefinitions(tools);
    
    // LLM返回JSON决策
    String raw = textGenerator.generate(..., input, ...);
    return parseDecision(raw, tools, ...);  // 解析 {action: "TOOL", toolName: "xxx"}
}

// 降级策略：规则打分
private ReactDecision fallbackDecision(...) {
    for (McpToolDefinition tool : tools) {
        int score = fallbackToolScore(tool.name(), ...);
        // 关键词匹配 + 上下文打分
    }
}
```

### 2. 多Agent协作

#### 协调决策流程

```
用户问题 → ConversationalCoordinator.analyze()
                    │
                    ├─ 包含"安全/漏洞/注入/xss/sql/token/secret"？ → security-specialist
                    ├─ 包含"性能/慢/优化/复杂度/查询/吞吐"？ → performance-specialist
                    ├─ 包含"规范/pdf/标准/文档"或context有相关规范？ → rag-specialist
                    ├─ 包含"审查/review/代码/bug/重构"？ → review-specialist
                    └─ 默认 → review-specialist
                    
                    │  简单问题？(无上下文，消息简短)
                    ├─ YES → SIMPLE_ANSWER (直接回答)
                    └─ NO → SINGLE_AGENT / MULTI_AGENT_COLLABORATION
```

#### SpecialistAgent类型

| Agent | 职责 | 关键词 | 系统提示 |
|-------|------|--------|----------|
| ReviewSpecialistAgent | 代码审查、Bug检测、重构建议 | review, 代码, bug, 重构 | 代码审查与Bug检测专家 |
| SecuritySpecialistAgent | 安全漏洞、注入、敏感信息泄露 | 安全, 漏洞, 注入, xss, sql, token, secret | 专注于 secrets、injection、authorization gaps |
| PerformanceSpecialistAgent | 性能瓶颈、复杂度、数据库查询 | 性能, 慢, 优化, 复杂度, 查询, 吞吐 | 专注于 algorithmic complexity、hot loops、N+1 |
| RagSpecialistAgent | 规范检索、知识库问答 | 规范, pdf, 标准, 文档 | 基于项目规范和历史审查提供证据 |
| DocumentationSpecialistAgent | 文档生成与规范 | 文档, 说明 | 文档专家 |
| ArchitectureSpecialistAgent | 架构分析与设计 | 架构, 设计, 模块 | 架构专家 |
| GeneralCodingAgent | 通用编码问题 | 编码, 实现 | 通用编码Agent |
| LocalCodeSpecialistAgent | 本地代码分析 | 本地代码 | 本地代码专家 |

### 3. RAG 混合检索

#### 检索流程

```
用户查询
    │
    ├─ 1. QueryRewriter 重写Query
    │      中文分词 + 同义词扩展 + 英文翻译
    │      "代码太乱" → ["code quality", "refactoring", "代码质量", "重构"]
    │
    ├─ 2. 语义召回 (Milvus向量库)
    │      embedding → 向量相似度搜索 → topK*2
    │
    ├─ 3. 关键词召回 (BM25)
    │      TOKEN_PATTERN正则分词 → IDF计算 → BM25打分 → topK*2
    │      BM25参数: k1=1.2, b=0.75
    │
    └─ 4. RRF融合排序 (Reciprocal Rank Fusion)
           score(d) = Σ 1/(k + rank_i(d))
           语义权重1.0, 关键词权重1.15 (关键词略优先)
           k=60, 输出最终topK
```

### 4. 工具注册中心 (MCP)

#### 已注册工具

| 工具名 | 功能 | 参数 |
|--------|------|------|
| git_diff_fetch | 获取Git仓库Diff | repoUrl, branch, language |
| sonar_scan | Sonar风格代码扫描 | projectKey, branch |
| code_refactor | 生成重构代码 | originalCode, suggestions[] |
| review_history_search | 搜索历史审查 | query, projectId, sessionId, topK |
| session_memory_read | 读取会话记忆 | sessionId |
| chat_history_search | 语义搜索聊天历史 | query, sessionId, topK |
| norm_search | 搜索PDF规范 | query, projectId, limit |
| file_operation | 文件操作 | operation, path, content |
| code_search | 代码搜索 | query, language, filePatterns |
| web_search | Web搜索 | query, limit |
| local_file_operation | 本地文件操作 | operation, path |

#### 工具定义元数据

```java
public record McpToolDefinition(
    String name,           // 工具名称
    String description,    // 人类可读描述
    Map<String, Object> inputSchema  // 参数schema
)

// 注册示例
mcpClient.registerTool(definition(
    "sonar_scan",
    "Run a Sonar-style scan for the current project and branch.",
    Map.of("projectKey", "string", "branch", "string")
), params -> sonarClient.scan(...));
```

### 5. 对话记忆管理

#### 多层记忆架构

```
┌─────────────────────────────────────────────────────────────┐
│                      对话记忆系统                              │
├──────────────────┬──────────────────────────────────────────┤
│   滑动窗口记忆     │           长期记忆                        │
│  SlidingWindow   │      ChatHistoryRepository (Milvus+MySQL) │
│   Memory         │                                           │
├──────────────────┼──────────────────────────────────────────┤
│  内存: 20条/会话   │  Milvus向量库 + MySQL双存储               │
│  LRU淘汰策略      │  本地缓存500条                            │
│  当前会话        │  跨会话检索                               │
│  快速访问        │  支持语义搜索                             │
├──────────────────┼──────────────────────────────────────────┤
│              代码上下文记忆                                   │
│      SessionCodeContextMemory                                │
│  - 存储当前代码审查的上下文                                  │
│  - 支持代码片段、文件路径、Diff信息                           │
└──────────────────┴──────────────────────────────────────────┘
```

## API 接口

### 对话接口

#### 1. 快速问答 (同步)

```http
POST /api/chat/send
Content-Type: application/json

{
  "sessionId": "session-xxx",    // 可选
  "projectId": "project-001",   // 可选
  "message": "帮我检查这段代码"
}
```

#### 2. 深度分析 (SSE流式)

```http
POST /api/chat/react
Content-Type: application/json

{
  "sessionId": "session-xxx",
  "projectId": "project-001",
  "message": "详细分析这个仓库的安全问题"
}
```

**SSE事件流**:
```
event: connected
data: {"sessionId": "xxx", "projectId": "xxx"}

event: agent_start
data: {"agentId": "security-specialist", "name": "Security Specialist"}

event: thinking
data: {"step": 1, "type": "THINKING", "content": "分析安全问题..."}

event: tool_call
data: {"step": 2, "tool": "sonar_scan", "input": "...", "output": "..."}

event: agent_complete
data: {"agentId": "security-specialist", "content": "分析结论..."}

event: done
data: {"sessionId": "xxx", "content": "最终回答", "summary": "..."}
```

### 审查接口

#### 提交审查

```http
POST /api/review/submit
Content-Type: application/json

{
  "type": "GIT_DIFF",           // 或 "PASTE_CODE"
  "repoUrl": "https://github.com/xxx/yyy",
  "branch": "main",
  "projectId": "project-001",
  "language": "java"
}
```

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0
- Redis 7.0
- Milvus 3.0 (可选，使用内置向量检索)

### 1. 克隆项目

```bash
git clone https://github.com/your-org/code-review-agent.git
cd code-review-agent
```

### 2. 配置数据库

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/code_review?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
  redis:
    host: localhost
    port: 6379
```

### 3. 配置API Key

```yaml
# application.yml
spring:
  ai:
    dashscope:
      api-key: your-api-key
```

### 4. 启动后端

```bash
cd code-review-start
mvn spring-boot:run
```

### 5. 启动前端

```bash
cd front
npm install
npm run dev
```

访问 `http://localhost:5173` 即可使用。

## 设计模式与最佳实践

### 1. Agent模式

```
Agent (接口)
    │
    └── BaseAgent (抽象基类)
            │
            ├── ConversationalCoordinator      # 对话协调者(路由+意图分析)
            │
            ├── SpecializedAgent (抽象基类)    # 专业Agent
            │   ├── ReviewSpecialistAgent       # 审查专家
            │   ├── SecuritySpecialistAgent      # 安全专家
            │   ├── PerformanceSpecialistAgent  # 性能专家
            │   ├── RagSpecialistAgent           # 知识库专家
            │   ├── DocumentationSpecialistAgent  # 文档专家
            │   ├── ArchitectureSpecialistAgent   # 架构专家
            │   ├── GeneralCodingAgent           # 通用编码
            │   └── LocalCodeSpecialistAgent     # 本地代码
            │
            ├── ReActAgent                      # 深度分析(ReAct循环)
            ├── SimpleAgent                     # 快速回答
            ├── FlowAgent                       # Flow编排
            ├── ReviewAgent                     # 审查Agent
            ├── AdvisorAgent                    # 顾问Agent
            ├── RefactorAgent                   # 重构Agent
            └── SummarizerAgent                 # 总结Agent
```

每个SpecialistAgent：
- 定义自己的系统提示词 (getSpecialistSystemPrompt)
- 定义支持的意图类型 (supportedIntents)
- 定义偏好工具列表 (preferredToolNames)
- 实现fallback分析逻辑

### 2. 策略模式

工具决策采用策略模式：
- `planWithModel()`: LLM动态决策策略
- `fallbackDecision()`: 规则打分降级策略

### 3. 观察者模式

```java
ReactStreamListener
    ├── onAgentStart()      // Agent开始
    ├── onStep()            // 思考/工具步骤
    ├── onAgentComplete()   // Agent完成
    └── onComplete()       // 整体完成
```

### 4. 模板方法模式

`SpecializedAgent` 定义工具决策模板：
```java
public final ReactDecision decideNextAction(...) {
    ReactDecision planned = planWithModel(...);  // 钩子方法
    if (planned != null) return planned;
    return fallbackDecision(...);
}

protected abstract List<String> preferredToolNames();  // 抽象方法
```

### 5. 回溯规划模式

BacktrackingPlanner 支持执行路径回溯：
```java
BacktrackingPlanner
    ├── planSubTasks()      // 规划子任务
    ├── canBacktrack()      // 是否可回溯
    └── backtrack()         // 执行回溯
```

### 6. 自我反思模式

SelfReflection 允许Agent审视自身输出：
```java
SelfReflection
    ├── reflect()           // 执行反思
    └── shouldRetry()       // 是否需要重试
```

## 涉及的核心知识

### 1. ReAct (Reasoning + Acting)

ReAct是一种让LLM进行多步推理并与外部工具交互的范式：

```
Think: 分析当前情况，决定下一步行动
 ↓
Act:   调用工具获取信息
 ↓
Observe: 观察工具返回结果，更新认知
 ↓
循环直到得到满意答案
```

### 2. RAG (Retrieval Augmented Generation)

RAG通过检索增强生成质量：

```
用户查询 → 检索相关知识 → 结合上下文 → LLM生成回答
              ↓
      向量数据库 / 传统检索
```

### 3. BM25 (Best Matching 25)

BM25是一种基于词频的经典检索算法：

```
score(D, Q) = Σ IDF(qi) · (tf(qi, D) · (k1 + 1)) / (tf(qi, D) + k1 · (1 - b + b · |D|/avgdl))
```

### 4. RRF (Reciprocal Rank Fusion)

RRF用于融合多个检索结果：

```
RRF_score(d) = Σ 1 / (k + rank_i(d))
```

### 5. 滑动窗口记忆

固定大小的记忆窗口，新内容进入、旧内容移出：

```
[1, 2, 3, 4, 5] → 窗口大小=3 → [3, 4, 5]
```

### 6. 混合检索策略

结合多种检索方法提升召回率：

```
语义检索 (向量) + 关键词检索 (BM25) → RRF融合 → 最终结果
```

### 7. MCP (Model Context Protocol)

MCP是一种让AI模型与外部工具交互的标准协议：

- 工具注册：统一注册、发现
- 工具调用：标准化输入输出
- 元数据：描述、参数schema

## 配置说明

### 工具注册配置

```java
// ToolRegistrationConfig.java
@PostConstruct
public void registerTools() {
    mcpClient.registerTool(definition(
        "git_diff_fetch",
        "Fetch the latest repository diff",
        Map.of("repoUrl", "string", "branch", "string", "language", "string")
    ), params -> gitDiffFetcher.fetchDiff(...));
}
```

### Agent偏好配置

```java
// SecuritySpecialistAgent.java
@Override
protected List<String> preferredToolNames() {
    return List.of("git_diff_fetch", "sonar_scan", "review_history_search", 
                   "session_memory_read", "chat_history_search", "norm_search");
}
```

### 迭代参数配置

```java
// ReactLoop.java
private static final int MAX_ITERATIONS = 8;  // 最大迭代次数

// SpecializedAgent.java
protected int maxToolCalls() {
    return 3;  // 默认最大工具调用次数
}
```

## 扩展指南

### 添加新的SpecialistAgent

1. 继承 `SpecializedAgent`
2. 实现抽象方法
3. 注册为Spring Bean

```java
@Component
public class DatabaseSpecialistAgent extends SpecializedAgent {
    
    @Override
    public String specialistId() {
        return "database-specialist";
    }
    
    @Override
    protected List<String> preferredToolNames() {
        return List.of("sql_analysis", "query_explain");
    }
    
    @Override
    protected String fallbackAnalysis(...) {
        // 实现fallback逻辑
    }
}
```

### 添加新的MCP工具

1. 实现工具执行逻辑
2. 在 `ToolRegistrationConfig` 注册

```java
mcpClient.registerTool(definition(
    "my_new_tool",
    "Description of what it does",
    Map.of("param1", "string", "param2", "integer")
), params -> myService.execute(params));
```

### 更换Embedding模型

```yaml
# application.yml
spring:
  ai:
    embedding:
      provider: dashscope
      model: text-embedding-v3
```

## License

MIT License
