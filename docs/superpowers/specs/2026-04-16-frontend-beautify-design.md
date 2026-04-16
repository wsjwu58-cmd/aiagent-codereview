# 前端界面美化设计规范

**版本**: 1.0
**日期**: 2026-04-16
**方向**: 深空 HUD 风格 — 星际飞船控制台

---

## 1. 概述

将现有 React + Ant Design 前端从"蓝色渐变暗色主题"升级为"深空 HUD 风格"，营造星际飞船控制台的科技感。

**主要变更**：
- 色彩系统重构（深空蓝黑 + 冷蓝/青色调）
- 新增发光边框与光晕效果
- 页面缩减：从 5 个减少到 3 个（移除历史检索、知识库）
- 排版优化：收紧间距，统一圆角

---

## 2. 色彩系统

### 2.1 背景色阶

| Token | 色值 | 用途 |
|-------|------|------|
| `--bg-void` | #0a0e17 | 主背景（深空） |
| `--bg-hull` | #111827 | 舱体灰（卡片、侧边栏） |
| `--bg-panel` | #0f172a | 面板背景 |
| `--bg-input` | #0a0e17 | 输入框背景 |

### 2.2 强调色阶

| Token | 色值 | 用途 |
|-------|------|------|
| `--accent-cyan` | #38bdf8 | 主要强调色（冷蓝） |
| `--accent-glow` | #22d3ee | 发光效果（青蓝） |
| `--accent-dim` | #0ea5e9 | 次要强调 |
| `--accent-gradient` | linear-gradient(135deg, #38bdf8 0%, #22d3ee 100%) | 渐变 |

### 2.3 文字色阶

| Token | 色值 | 用途 |
|-------|------|------|
| `--text-star` | #e2e8f0 | 主文字（冷白） |
| `--text-dim` | #94a3b8 | 次要文字 |
| `--text-muted` | #64748b | 辅助文字 |

### 2.4 边框与效果

| Token | 色值 | 用途 |
|-------|------|------|
| `--border-hull` | #1e293b | 边框色 |
| `--border-glow` | rgba(56, 189, 248, 0.3) | 发光边框 |
| `--glow-sm` | 0 0 10px rgba(56, 189, 248, 0.2) | 小发光 |
| `--glow-md` | 0 0 20px rgba(56, 189, 248, 0.15) | 中发光 |
| `--glow-lg` | 0 0 30px rgba(56, 189, 248, 0.1) | 大发光 |

---

## 3. 视觉效果

### 3.1 发光边框效果

```css
border: 1px solid var(--border-hull);
box-shadow:
  0 0 0 1px var(--border-glow) inset,
  0 0 20px rgba(56, 189, 248, 0.1);
```

Hover 状态：
```css
border-color: var(--accent-cyan);
box-shadow:
  0 0 0 1px var(--border-glow) inset,
  0 0 25px rgba(56, 189, 248, 0.2);
```

### 3.2 渐变边框（可选高端效果）

```css
border: 1px solid transparent;
background:
  linear-gradient(var(--bg-hull), var(--bg-hull)) padding-box,
  linear-gradient(135deg, var(--accent-cyan), var(--accent-glow)) border-box;
```

---

## 4. 布局规范

### 4.1 间距系统

| Token | 值 | 用途 |
|-------|-----|------|
| `--space-xs` | 4px | 紧凑间距 |
| `--space-sm` | 8px | 小间距 |
| `--space-md` | 12px | 中间距 |
| `--space-lg` | 16px | 大间距 |
| `--space-xl` | 24px | 特大间距 |

### 4.2 圆角系统

| Token | 值 | 用途 |
|-------|-----|------|
| `--radius-sm` | 6px | 小圆角（按钮、输入框） |
| `--radius-md` | 8px | 中圆角（卡片、面板） |
| `--radius-lg` | 12px | 大圆角（模态框） |

### 4.3 页面结构

```
┌─────────────────────────────────────────────┐
│  Header: logo + 用户信息 + 退出按钮          │  高度: 64px
├─────────────────────────────────────────────┤
│  Menu 导航                                  │  高度: 48px
├─────────────────────────────────────────────┤
│                                             │
│  Content Area                               │  flex: 1
│                                             │
└─────────────────────────────────────────────┘
```

---

## 5. 组件改造

### 5.1 Header

- 背景: 渐变 from #111827 to #0a0e17
- 底部: 1px 发光线条（--accent-cyan, 50% 透明度）
- Logo: 保持现有图标，增加微弱光晕
- 标题: 渐变文字（可选）

### 5.2 导航 Menu

- 横向排列，圆角胶囊感
- 背景: 透明
- 选中态: 冷蓝背景 20% 透明度 + 发光边框
- Hover: 冷蓝边框

### 5.3 卡片 (page-card)

- 背景: --bg-hull
- 边框: 1px solid --border-hull + 发光效果
- Hover: 边框变亮 --accent-cyan

### 5.4 按钮

**主按钮**:
- 背景: --accent-gradient
- 阴影: --glow-sm
- Hover: 边框加亮，阴影加强

**次按钮**:
- 背景: 透明
- 边框: 1px solid --accent-cyan
- Hover: 背景 rgba(56, 189, 248, 0.1)

### 5.5 消息气泡

**用户消息**:
- 背景: rgba(56, 189, 248, 0.15)
- 左边框: 3px solid --accent-cyan
- 文字: --text-star

**AI 消息**:
- 背景: --bg-hull
- 左边框: 3px solid --accent-glow
- 文字: --text-star

### 5.6 Thinking Panel

- 背景: rgba(34, 211, 238, 0.05)
- 边框: 1px solid rgba(34, 211, 238, 0.2)
- 顶部: 2px solid --accent-glow
- 步骤竖线: --accent-glow（tool 步骤用 --accent-cyan）

### 5.7 输入框

- 背景: --bg-void
- 边框: 1px solid --border-hull
- Focus: 边框变 --accent-cyan，添加发光

### 5.8 会话列表

- 背景: --bg-hull
- 选中项: 左边框 3px --accent-cyan + 背景 rgba(56, 189, 248, 0.1)
- Hover: 背景 rgba(255, 255, 255, 0.02)

---

## 6. 页面变更

### 6.1 删除的页面

- `/history` — 历史检索
- `/knowledge` — 知识库

### 6.2 导航菜单（更新后）

```typescript
const menuItems = [
  { key: 'dashboard', label: '工作台' },
  { key: 'review', label: '代码审查' },
  { key: 'chat', label: '对话工作台' },
]
```

### 6.3 需要修改的文件

1. `src/App.tsx` — 更新 menuItems，移除 history/knowledge 渲染
2. `src/styles.css` — 更新 CSS 变量和组件样式
3. `src/pages/History/` — 可删除
4. `src/pages/Knowledge/` — 可删除

---

## 7. 动效建议

### 7.1 微动效

- 边框发光: 过渡 200ms ease
- 卡片 Hover: transform translateY(-2px), 200ms
- 按钮 Hover: 轻微上移 + 阴影加强
- 消息出现: fadeIn + slideUp, 300ms

### 7.2 可选动效（高端）

- 扫描线: 顶部光线从左到右循环，8s 周期
- 背景网格: 极淡 #1e293b 格子纹理

---

## 8. 技术实现

### 8.1 CSS 变量替换

现有变量 → 新变量：

| 旧变量 | 新变量 |
|--------|--------|
| --bg-primary | --bg-void |
| --bg-secondary | --bg-hull |
| --bg-card | --bg-hull |
| --accent-primary | --accent-cyan |
| --accent-secondary | --accent-glow |
| --text-primary | --text-star |
| --border-color | --border-hull |

### 8.2 优先级

1. 更新 `styles.css` 中的 CSS 变量
2. 更新 `App.tsx` 中的 ConfigProvider theme
3. 删除历史检索和知识库相关代码
4. 调整间距和圆角
5. 添加发光边框效果

---

## 9. 验收标准

- [ ] 主背景变为深空蓝黑 #0a0e17
- [ ] 强调色统一为冷蓝/青蓝色系
- [ ] 卡片具有发光边框效果
- [ ] 导航菜单为胶囊选中态
- [ ] 页面从 5 个减少到 3 个
- [ ] 间距系统统一为 8px 基准
- [ ] 整体呈现星际飞船控制台质感
