# 代码审查增强功能设计文档

**日期**: 2026-04-18
**版本**: v1.0

---

## 一、概述

本文档描述三项代码审查系统增强功能的设计方案：
1. PDF 审查报告导出
2. 本地代码审查与自动修复
3. RAG 文件管理页面

---

## 二、功能一：PDF 审查报告导出

### 2.1 目标

用户可在代码审查完成后，一键将审查报告导出为 PDF 文件，包含评分、风险漏洞、总结、代码重构建议等内容。

### 2.2 技术方案

**后端**

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| PDF 生成库 | OpenPDF ( LGPL/MPL) | 轻量级、无需本地安装、API 简洁 |
| 模板渲染 | 手写 PDF 布局代码 | 不引入额外模板引擎依赖 |

**核心流程**

```
ReviewService.submit() 完成审查
  → 用户点击「导出PDF」按钮
  → 前端请求 GET /api/reviews/{reviewId}/pdf
  → PdfExportService 生成 PDF 并返回
  → 浏览器直接下载
```

**PDF 内容结构**

```
┌─────────────────────────────────────┐
│  智能代码审查报告                      │
│  Review ID: review_xxx              │
│  生成时间: 2026-04-18 10:30         │
├─────────────────────────────────────┤
│  ## 综合结论                          │
│  评分: 72/100                        │
│  问题总数: 8                          │
│  严重: 1  高: 3  中: 2  低: 2        │
├─────────────────────────────────────┤
│  ## 关键问题                          │
│  1. [SEVERITY] 文件: xxx.java:45     │
│     问题: ...                         │
│     建议: ...                         │
│  2. ...                              │
├─────────────────────────────────────┤
│  ## 代码重构建议                       │
│  - P1: xxx 建议将...                 │
│  - P2: ...                           │
├─────────────────────────────────────┤
│  ## 重构代码预览                       │
│  ```java                             │
│  // 重构后代码                        │
│  ```                                 │
├─────────────────────────────────────┤
│  ## 协同分析补充                       │
│  Security: ...                       │
│  Performance: ...                    │
└─────────────────────────────────────┘
```

**API 设计**

```
GET /api/reviews/{reviewId}/pdf
Response: application/pdf
Header: Content-Disposition: attachment; filename="review_{reviewId}.pdf"
```

**新增文件**

| 文件路径 | 职责 |
|----------|------|
| `code-review-api/src/main/java/.../service/PdfExportService.java` | PDF 生成逻辑 |
| `code-review-api/src/main/java/.../controller/ReviewController.java` | 新增导出接口 |

### 2.3 前端变更

| 变更点 | 说明 |
|--------|------|
| `ReviewReport/index.tsx` | 添加「导出PDF」按钮 |
| 点击按钮 | 发起下载请求，浏览器自动下载 |

---

## 三、功能二：本地代码审查与自动修复

### 3.1 目标

用户可在对话工作台中选择本地文件夹，指定代码文件让 Agent 分析漏洞和错误。Agent 可直接修改本地文件进行重构，实现类似 Claude Code 的终端式体验。

### 3.2 技术方案

**后端**

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| 本地文件操作 | Java NIO + Spring `@Value` 注入允许目录白名单 | 安全隔离，仅允许操作配置目录 |
| Agent | `LocalCodeSpecialistAgent` 继承 `SpecializedAgent` | 新增文件读写工具 |
| 流式输出 | SSE (已支持) | 复用现有 `useReActStream` hook |

**目录安全限制**

```java
// application.yml
code-review:
  allowed-local-paths:
    - D:/projects
    - C:/workspace
    - /home/user/projects
```

Agent 仅能在白名单目录下操作文件，防止越权访问系统文件。

**工具定义**

```java
// LocalFileOperationTool
- list_files(path, extensions)     // 列出目录下文件
- read_file(path)                  // 读取文件内容
- write_file(path, content)        // 写入/覆盖文件
- create_file(path, content)       // 创建新文件
- delete_file(path)               // 删除文件（需用户确认）
- search_files(path, pattern)     // 搜索文件
```

**核心流程**

```
用户选择文件夹
  → 前端传递 folderPath 到后端
  → LocalCodeAnalysisService 初始化项目上下文
  → 创建 SSE 连接，流式返回 Agent 思考过程
  → Agent 分析代码 → 发现问题 → 提出修复方案
  → (如需写入) Agent 请求授权或直接写入（配置可调）
  → 前端实时显示文件变更
```

**API 设计**

```
POST /api/local-code/analyze
Body: { "folderPath": "D:/projects/myapp", "fileFilters": ["*.java", "*.xml"] }
Response: SSE (text/event-stream)

SSE 事件:
- connected: { "sessionId": "xxx" }
- thinking: { "step": 1, "content": "正在扫描文件..." }
- tool_call: { "tool": "list_files", "input": "...", "output": "..." }
- file_modified: { "path": "src/main.java", "action": "modified" }
- done: { "summary": "...", "modifiedFiles": [...] }
- error: { "message": "..." }
```

**新增文件**

| 文件路径 | 职责 |
|----------|------|
| `code-review-tools/src/main/java/.../tools/file/LocalFileOperationTool.java` | 本地文件操作工具 |
| `code-review-core/src/main/java/.../core/agent/specialized/LocalCodeSpecialistAgent.java` | 本地代码分析 Agent |
| `code-review-api/src/main/java/.../service/LocalCodeAnalysisService.java` | 本地代码分析服务 |
| `code-review-api/src/main/java/.../controller/LocalCodeController.java` | 控制器 |

### 3.3 前端变更

| 变更点 | 说明 |
|--------|------|
| `pages/Chat/Workspace.tsx` | 新增「本地代码审查」按钮 |
| 新增 `components/FolderSelector/index.tsx` | 文件夹选择器组件 |
| 新增 `components/LocalCodeTerminal/index.tsx` | 终端式流式输出组件 |
| `hooks/useLocalCodeStream.ts` | 管理本地代码分析 SSE 连接 |
| 路由变更 | `/chat` 页面增加本地代码审查 Tab |

**交互设计**

```
┌──────────────────────────────────────────────────────────────┐
│  对话工作台                                                   │
├──────────────────────────────────────────────────────────────┤
│  [快速问答] [深度分析] [本地代码审查]                            │
├──────────────────────────────────────────────────────────────┤
│  📁 选择文件夹: [________________________] [选择]              │
│  文件过滤: [*.java, *.xml________________]                     │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  > 正在扫描 D:/projects/myapp/src/main/java...             │ │
│  > 找到 23 个 Java 文件                                     │ │
│  > 开始分析 UserService.java...                             │ │
│  > [TOOL] read_file(path=UserService.java)                 │ │
│  > 发现潜在 SQL 注入漏洞: 第 45 行                           │ │
│  > 建议修复方案: 使用 PreparedStatement                       │ │
│  > 是否修改? (y/n)                                          │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  [停止]  [授权自动修复]                                        │
└──────────────────────────────────────────────────────────────┘
```

**Agent 写文件授权模式**

| 模式 | 行为 |
|------|------|
| `ask` (默认) | Agent 每次写文件前请求用户确认 |
| `auto` | Agent 直接写入文件，用户可随时停止 |
| 配置项 | `code-review.local-code.auto-write: false` |

---

## 四、功能三：RAG 文件管理页面

### 4.1 目标

提供独立页面查看和删除 RAG 中存储的数据，包括审查历史、PDF 规范文档、聊天记录。

### 4.2 技术方案

**后端 API**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/knowledge/records` | GET | 分页查询所有 RAG 记录 |
| `/api/knowledge/records/{id}` | DELETE | 删除单条记录 |
| `/api/knowledge/records/batch` | POST | 批量删除 |

**查询参数**

```
GET /api/knowledge/records?type=review&projectId=xxx&keyword=xxx&page=0&size=20
```

**响应结构**

```json
{
  "content": [
    {
      "id": "record_xxx",
      "type": "review_history",
      "projectId": "proj_xxx",
      "summary": "修复了登录逻辑...",
      "createdAt": "2026-04-18T10:00:00Z",
      "metadata": {
        "score": 85,
        "totalIssues": 5
      }
    },
    {
      "id": "pdf_xxx",
      "type": "pdf_norm",
      "projectId": "proj_xxx",
      "summary": "阿里巴巴Java开发手册",
      "fileName": "alibaba-java-guide.pdf",
      "createdAt": "2026-04-15T08:00:00Z"
    },
    {
      "id": "chat_xxx",
      "type": "chat_history",
      "projectId": "proj_xxx",
      "sessionId": "session_xxx",
      "summary": "用户询问如何优化查询性能",
      "createdAt": "2026-04-17T14:30:00Z"
    }
  ],
  "totalElements": 156,
  "totalPages": 8,
  "page": 0,
  "size": 20
}
```

**数据删除策略**

- 审查历史：同时删除 Milvus 向量记录 + MySQL 记录
- PDF 规范：同时删除文件存储 + 向量 + MySQL 记录
- 聊天记录：同时删除 Milvus 向量 + MySQL 记录

**新增文件**

| 文件路径 | 职责 |
|----------|------|
| `code-review-api/src/main/java/.../controller/KnowledgeController.java` | 扩展知识管理接口 |
| `code-review-api/src/main/java/.../service/KnowledgeManageService.java` | RAG 记录管理服务 |

### 4.3 前端变更

| 变更点 | 说明 |
|--------|------|
| 新增 `pages/KnowledgeManage/index.tsx` | RAG 文件管理页面 |
| 侧边栏新增导航项 | 「知识库管理」 |
| 路由配置 | `/knowledge-manage` → `KnowledgeManagePage` |
| 新增 `services/knowledgeApi.ts` | 知识库管理 API 调用 |

**页面布局**

```
┌──────────────────────────────────────────────────────────────┐
│  知识库管理                                                   │
├──────────────────────────────────────────────────────────────┤
│  [全部] [审查历史] [PDF规范] [聊天记录]    [搜索: ________]     │
├──────────────────────────────────────────────────────────────┤
│  ☐  ID         类型      项目ID       摘要         创建时间   │
│  ☐  rec_xxx    审查历史   proj_1     修复登录...   2026-04-18│
│  ☐  pdf_xxx    PDF规范   proj_1     阿里开发手册   2026-04-15│
│  ☐  chat_xxx   聊天记录   proj_2     优化查询性能   2026-04-17│
│  ...                                                         │
├──────────────────────────────────────────────────────────────┤
│  已选择 3 项                        [删除选中] [刷新]          │
└──────────────────────────────────────────────────────────────┘
```

---

## 五、架构变更汇总

### 5.1 新增模块/文件

```
code-review-tools/
└── src/main/java/com/heima/codereview/tools/
    └── file/
        └── LocalFileOperationTool.java     # 本地文件操作工具

code-review-core/
└── src/main/java/com/heima/codereview/core/agent/
    └── specialized/
        └── LocalCodeSpecialistAgent.java   # 本地代码分析 Agent

code-review-api/
├── src/main/java/com/heima/codereview/api/
│   ├── service/
│   │   ├── PdfExportService.java          # PDF 导出服务
│   │   ├── LocalCodeAnalysisService.java  # 本地代码分析服务
│   │   └── KnowledgeManageService.java    # 知识库管理服务
│   └── controller/
│       ├── ReviewController.java          # 新增导出接口
│       ├── LocalCodeController.java       # 本地代码分析控制器
│       └── KnowledgeController.java      # 扩展知识管理接口
```

### 5.2 前端新增文件

```
front1/src/
├── pages/
│   └── KnowledgeManage/
│       └── index.tsx                      # 知识库管理页面
├── components/
│   ├── FolderSelector/
│   │   └── index.tsx                      # 文件夹选择器
│   └── LocalCodeTerminal/
│       └── index.tsx                      # 终端式输出组件
├── hooks/
│   └── useLocalCodeStream.ts               # 本地代码 SSE hook
└── services/
    └── knowledgeApi.ts                     # 知识库 API
```

### 5.3 配置变更

**application.yml 新增**

```yaml
code-review:
  pdf:
    author: "智能代码审查系统"
    output-path: /tmp  # 仅用于临时文件

  local-code:
    allowed-paths:
      - D:/projects
      - C:/workspace
    auto-write: false  # 是否自动写入，false=每次确认
```

---

## 六、依赖变更

### 6.1 Maven 新增依赖

```xml
<!-- code-review-api/pom.xml -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>1.3.30</version>
</dependency>
```

### 6.2 前端新增依赖

无需新增依赖，复用现有技术栈（React 18 + Ant Design + SSE）。

---

## 七、测试策略

| 层级 | 测试内容 |
|------|----------|
| 单元测试 | `PdfExportService` PDF 内容生成 |
| 单元测试 | `LocalFileOperationTool` 文件操作 |
| 集成测试 | 白名单目录外访问被拒绝 |
| 集成测试 | SSE 流式输出完整性 |
| E2E 测试 | PDF 下载后内容验证 |
| E2E 测试 | 本地代码分析 → 修改 → 文件实际变更 |

---

## 八、风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Agent 误删/覆盖重要文件 | 白名单限制 + `auto-write: false` 默认每次确认 |
| 大文件导致内存溢出 | 流式读取 + 限制单文件最大 10MB |
| PDF 生成中文乱码 | 使用内置中文字体（Noto Sans CJK） |
| Milvus 删除后数据不一致 | 事务删除：先删 MySQL 再删 Milvus，失败回滚 |
