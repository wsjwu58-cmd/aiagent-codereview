package com.heima.codereview.core.agent.planning;

import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 意图分析与任务规划器
 * 支持关键词快速预筛 + LLM细粒度分类
 */
@Component
public class IntentTaskPlanner {

    // 复杂度阈值，超过则使用LLM进行任务规划
    private static final int COMPLEXITY_THRESHOLD = 40;

    private final LlmIntentClassifier intentClassifier;
    private final LlmTaskPlanner taskPlanner;

    /** 无参构造函数，用于兼容测试和降级场景 */
    public IntentTaskPlanner() {
        this(null, null);
    }

    public IntentTaskPlanner(LlmIntentClassifier intentClassifier, LlmTaskPlanner taskPlanner) {
        this.intentClassifier = intentClassifier;
        this.taskPlanner = taskPlanner;
    }

    /**
     * 分析用户意图
     * @param forceReview 是否强制审查模式
     */
    public IntentAnalysisResult analyzeIntent(String userMessage, ConversationContext context, boolean forceReview) {
        // 有仓库上下文时使用LLM分类
        if (forceReview || (context != null && context.hasRepositoryContext())) {
            return analyzeWithLLM(userMessage, context, forceReview);
        }
        // 简单请求使用关键词匹配
        return analyzeWithKeywords(userMessage, context, forceReview);
    }

    public IntentAnalysisResult analyzeIntent(String userMessage, ConversationContext context) {
        return analyzeIntent(userMessage, context, false);
    }

    /** 使用LLM进行意图分析 */
    private IntentAnalysisResult analyzeWithLLM(String userMessage, ConversationContext context, boolean forceReview) {
        // LLM组件不可用时降级到关键词
        if (intentClassifier == null) {
            return analyzeWithKeywords(userMessage, context, forceReview);
        }

        IntentClassification classification = intentClassifier.analyze(userMessage, context);

        IntentType primary = classification.primary();
        List<IntentType> intents = new ArrayList<>(classification.candidates());

        if (!intents.contains(primary)) {
            intents.add(0, primary);
        }

        // 计算复杂度分数
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

    /** 使用关键词进行意图分析 */
    private IntentAnalysisResult analyzeWithKeywords(String userMessage, ConversationContext context, boolean forceReview) {
        ConversationContext safeContext = context == null
                ? new ConversationContext("", "", "", "", "", "", "", List.of(), List.of(), List.of(), List.of())
                : context;
        String normalized = safe(userMessage).toLowerCase(Locale.ROOT);
        List<IntentType> intents = new ArrayList<>();

        if (forceReview || containsAny(normalized, "review", "审查", "代码质量", "重构", "diff")) {
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
            if (!forceReview && !safeContext.hasRepositoryContext() && normalized.length() <= 18) {
                intents.add(IntentType.SIMPLE_ANSWER);
            } else {
                intents.add(IntentType.GENERAL_CODING);
            }
        }

        IntentType primaryIntent = intents.get(0);
        int complexityScore = Math.min(100,
                15 + intents.size() * 20 + (safeContext.hasRepositoryContext() ? 15 : 0) + (hasRelevantNorms(safeContext) ? 10 : 0));
        boolean requiresRepositoryContext = forceReview
                || primaryIntent == IntentType.CODE_REVIEW
                || primaryIntent == IntentType.ARCHITECTURE_ANALYSIS
                || primaryIntent == IntentType.DOCUMENTATION_GENERATION
                || safeContext.hasRepositoryContext();
        boolean requiresReflection = complexityScore >= 30 || intents.size() > 1;
        boolean requiresBacktracking = complexityScore >= 50 || intents.size() > 2;
        String rationale = "Intent=" + primaryIntent + ", candidates=" + intents + ", repositoryContext=" + safeContext.hasRepositoryContext();
        return new IntentAnalysisResult(primaryIntent, intents, complexityScore, requiresRepositoryContext, requiresReflection, requiresBacktracking, rationale);
    }

    /**
     * 任务分解
     * @deprecated 对于复杂请求，建议使用 planTasks() 方法进行LLM自主规划
     */
    public List<SubTask> decomposeTask(IntentAnalysisResult intent, ConversationContext context) {
        if (intent == null) {
            return List.of();
        }
        List<SubTask> tasks = new ArrayList<>();
        IntentType primary = intent.primaryIntent();
        tasks.add(createPrimaryTask(primary));
        if (intent.supports(IntentType.KNOWLEDGE_RETRIEVAL)
                && primary != IntentType.KNOWLEDGE_RETRIEVAL
                && context != null
                && hasRelevantNorms(context)) {
            tasks.add(SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.KNOWLEDGE_RETRIEVAL,
                    "Retrieve supporting norms",
                    "Pull in norms, history, or memory that can support the primary task.",
                    "rag-specialist",
                    0,
                    ""
            ));
        }
        return tasks;
    }

    /**
     * 构建初始任务列表
     * @deprecated 对于复杂请求，建议使用 planTasks() 方法
     */
    public List<SubTask> buildInitialTasks(IntentAnalysisResult intent) {
        if (intent == null) {
            return List.of();
        }

        // 复杂请求由FlowAgent调用planTasks进行LLM规划
        if (intent.complexityScore() >= COMPLEXITY_THRESHOLD && taskPlanner != null) {
            return List.of();
        }

        // 简单请求使用模板
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

    /**
     * 使用LLM进行任务规划
     * @param userMessage 用户原始请求
     * @param context 对话上下文
     * @param intent 意图分析结果
     * @param maxTasks 最大任务数
     */
    public PlannedTasks planTasks(String userMessage, ConversationContext context, IntentAnalysisResult intent, int maxTasks) {
        if (taskPlanner == null || intent == null) {
            return PlannedTasks.empty();
        }
        return taskPlanner.plan(userMessage, context, intent, maxTasks);
    }

    /** 检查LLM组件是否可用 */
    private boolean isLlmAvailable() {
        return intentClassifier != null && taskPlanner != null;
    }

    public boolean isParallelizable(IntentType intentType) {
        return intentType == IntentType.SECURITY_ANALYSIS
                || intentType == IntentType.PERFORMANCE_ANALYSIS
                || intentType == IntentType.ARCHITECTURE_ANALYSIS
                || intentType == IntentType.CODE_REVIEW;
    }

    public SubTask createPrimaryTask(IntentType intentType) {
        return switch (intentType) {
            case CODE_REVIEW -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.CODE_REVIEW,
                    "Run code review",
                    "Perform a structured code review on the available repository context.",
                    "review-specialist",
                    0,
                    ""
            );
            case SECURITY_ANALYSIS -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.SECURITY_ANALYSIS,
                    "Run security analysis",
                    "Inspect secrets, injection risks, authorization gaps, and dangerous execution paths.",
                    "security-specialist",
                    0,
                    ""
            );
            case PERFORMANCE_ANALYSIS -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.PERFORMANCE_ANALYSIS,
                    "Run performance analysis",
                    "Inspect hot loops, repeated queries, blocking calls, and scalability risks.",
                    "performance-specialist",
                    0,
                    ""
            );
            case ARCHITECTURE_ANALYSIS -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.ARCHITECTURE_ANALYSIS,
                    "Analyze architecture",
                    "Assess module boundaries, dependency direction, layering, and design trade-offs.",
                    "architecture-specialist",
                    0,
                    ""
            );
            case DOCUMENTATION_GENERATION -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.DOCUMENTATION_GENERATION,
                    "Generate documentation",
                    "Produce implementation-facing documentation based on repository context and requested scope.",
                    "documentation-specialist",
                    0,
                    ""
            );
            case KNOWLEDGE_RETRIEVAL -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.KNOWLEDGE_RETRIEVAL,
                    "Retrieve supporting knowledge",
                    "Search norms, history, and memory to support downstream analysis.",
                    "rag-specialist",
                    0,
                    ""
            );
            case SIMPLE_ANSWER, GENERAL_CODING -> SubTask.of(
                    IdUtils.withPrefix("task"),
                    IntentType.GENERAL_CODING,
                    "Solve coding request",
                    "Answer the programming question or debug issue with the available evidence.",
                    "general-coding-agent",
                    0,
                    ""
            );
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private boolean hasRelevantNorms(ConversationContext context) {
        return context != null && context.relevantNorms() != null && !context.relevantNorms().isEmpty();
    }
}
