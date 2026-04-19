package com.heima.codereview.core.agent;

import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.ReactStreamListener;
import com.heima.codereview.core.agent.conversational.SimpleAgent;
import com.heima.codereview.core.agent.conversational.SpecialistExecutionResult;
import com.heima.codereview.core.agent.conversational.SpecialistReport;
import com.heima.codereview.core.agent.planning.BacktrackingPlanner;
import com.heima.codereview.core.agent.planning.ExecutionHistory;
import com.heima.codereview.core.agent.planning.IntentAnalysisResult;
import com.heima.codereview.core.agent.planning.IntentType;
import com.heima.codereview.core.agent.planning.ReflectionResult;
import com.heima.codereview.core.agent.planning.SubTask;
import com.heima.codereview.core.agent.planning.TaskExecutionResult;
import com.heima.codereview.core.agent.react.ReactLoop;
import com.heima.codereview.core.agent.react.ReactResult;
import com.heima.codereview.core.agent.react.ThinkingStep;
import com.heima.codereview.core.agent.specialized.ReviewSpecialistAgent;
import com.heima.codereview.core.agent.specialized.ReviewSpecialistOutcome;
import com.heima.codereview.core.chain.ReviewContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class FlowAgent extends BaseAgent {

    private static final int MAX_PLANNED_TASKS = 6;
    private static final int MAX_TASK_DEPTH = 3;
    private static final int PARALLEL_TIMEOUT_SECONDS = 120;

    private final ReviewSpecialistAgent reviewSpecialistAgent;
    private final SimpleAgent simpleAgent;
    private final ReactLoop reactLoop;
    private final AgentTextGenerator textGenerator;
    private final Map<String, SpecialistAgent> specialistRegistry;
    private final ThreadPoolTaskExecutor specialistExecutor;

    public FlowAgent(ReviewSpecialistAgent reviewSpecialistAgent,
                     List<SpecialistAgent> specialists,
                     SimpleAgent simpleAgent,
                     ReactLoop reactLoop,
                     AgentTextGenerator textGenerator,
                     @Qualifier("specialistExecutor") ThreadPoolTaskExecutor specialistExecutor) {
        this.reviewSpecialistAgent = reviewSpecialistAgent;
        this.simpleAgent = simpleAgent;
        this.reactLoop = reactLoop;
        this.textGenerator = textGenerator;
        this.specialistRegistry = new LinkedHashMap<>();
        for (SpecialistAgent specialist : specialists) {
            this.specialistRegistry.put(specialist.getId(), specialist);
        }
        this.specialistExecutor = specialistExecutor;
    }

    @Override
    public String getName() {
        return "Planner Flow Agent";
    }

    public FlowResult execute(AgentExecutionContext context, AgentEventListener listener) {
        FlowResult result = new FlowResult();
        ReviewContext reviewContext = context.reviewContext();
        String sessionId = context.sessionId();
        ConversationContext conversationContext = toConversationContext(reviewContext);
        String userMessage = buildReviewRequest(reviewContext);
        IntentAnalysisResult intent = analyzeIntent(userMessage, conversationContext, true);

        listener.onEvent(sessionId, "agent_start", Map.of("agentId", getId(), "name", getName()));
        listener.onEvent(sessionId, "planner_intent", Map.of(
                "intent", intent.primaryIntent().name(),
                "rationale", intent.rationale()
        ));

        List<SpecialistReport> parallelReports = new ArrayList<>();
        List<SpecialistExecutionResult> parallelResults = new ArrayList<>();
        List<TaskExecutionResult> allTaskResults = new ArrayList<>();
        List<ThinkingStep> allSteps = new ArrayList<>();

        List<SubTask> initialTasks = buildInitialTasks(intent, userMessage);
        List<SubTask> supplementalTasks = initialTasks.stream()
                .filter(task -> task.intentType() != IntentType.CODE_REVIEW)
                .toList();

        if (!supplementalTasks.isEmpty()) {
            parallelReports = runParallelSpecialists(supplementalTasks, userMessage, conversationContext, intent, createBridgeListener(sessionId, listener), parallelResults, allTaskResults, allSteps);
        }

        ReviewSpecialistOutcome primaryOutcome = runStructuredReview(context, listener, result, allTaskResults);

        List<SpecialistReport> reports = new ArrayList<>(parallelReports);
        if (primaryOutcome != null) {
            reports.add(0, new SpecialistReport(reviewSpecialistAgent.getId(), reviewSpecialistAgent.getName(), safe(primaryOutcome.summary())));
        }

        List<SubTask> followupTasks = new ArrayList<>();
        for (TaskExecutionResult tr : allTaskResults) {
            if (tr.success() && tr.proposedSubTasks() != null) {
                for (SubTask proposal : tr.proposedSubTasks()) {
                    if (proposal.depth() > 0) {
                        followupTasks.add(proposal);
                    }
                }
            }
        }

        ExecutionHistory history = new ExecutionHistory();
        PlanningExecution followupExecution = runPlannerSequential(
                userMessage,
                conversationContext,
                intent,
                followupTasks,
                createBridgeListener(sessionId, listener),
                history,
                MAX_PLANNED_TASKS - 1
        );

        reports.addAll(followupExecution.reports());
        allTaskResults.addAll(followupExecution.taskResults());
        allSteps.addAll(followupExecution.steps());

        for (TaskExecutionResult taskResult : allTaskResults) {
            if (taskResult.success()) {
                result.getAgentOutputs().putIfAbsent(taskResult.agentId(), safe(taskResult.output()));
            }
        }

        String summary = aggregateResults(
                intent,
                conversationContext,
                result.getReport(),
                result.getAdvisorOutput(),
                result.getRefactoredCode(),
                allTaskResults,
                reports,
                primaryOutcome != null ? primaryOutcome.summary() : ""
        );
        result.setSummary(summary);
        if (result.getReport() != null && summary != null && !summary.isBlank()) {
            result.getReport().setSummary(summary);
        }
        listener.onEvent(sessionId, "agent_complete", Map.of("agentId", getId(), "name", getName(), "content", safe(summary)));
        listener.onEvent(sessionId, "done", Map.of("reviewId", reviewContext.reviewId()));
        return result;
    }

    private ReviewSpecialistOutcome runStructuredReview(AgentExecutionContext context,
                                                        AgentEventListener listener,
                                                        FlowResult result,
                                                        List<TaskExecutionResult> allTaskResults) {
        String sessionId = context.sessionId();
        SubTask reviewTask = SubTask.of(
                IdUtils.withPrefix("task"),
                IntentType.CODE_REVIEW,
                "Run structured code review",
                "Execute the existing review chain and produce refactoring guidance.",
                reviewSpecialistAgent.getId(),
                0,
                ""
        );
        listener.onEvent(sessionId, "agent_start", Map.of("agentId", reviewSpecialistAgent.getId(), "name", reviewSpecialistAgent.getName()));
        ReviewSpecialistOutcome primaryOutcome = reviewSpecialistAgent.review(context, listener);
        listener.onEvent(sessionId, "agent_complete", Map.of(
                "agentId", reviewSpecialistAgent.getId(),
                "name", reviewSpecialistAgent.getName(),
                "content", safe(primaryOutcome.summary())
        ));

        result.setReport(primaryOutcome.report());
        result.setAdvisorOutput(primaryOutcome.advisorOutput());
        result.setRefactoredCode(primaryOutcome.refactoredCode());
        result.getAgentOutputs().put(reviewSpecialistAgent.getId(), safe(primaryOutcome.summary()));
        result.getAgentOutputs().put("advisor-agent", safe(primaryOutcome.advisorOutput()));
        result.getAgentOutputs().put("refactor-agent", safe(primaryOutcome.refactoredCode()));

        allTaskResults.add(new TaskExecutionResult(
                reviewTask,
                reviewSpecialistAgent.getId(),
                reviewSpecialistAgent.getName(),
                true,
                safe(primaryOutcome.summary()),
                "",
                ReflectionResult.ok(),
                primaryOutcome.proposedSubTasks(),
                new SpecialistReport(reviewSpecialistAgent.getId(), reviewSpecialistAgent.getName(), safe(primaryOutcome.summary()))
        ));
        return primaryOutcome;
    }

    public ReactResult executeConversation(String userMessage,
                                           ConversationContext context,
                                           ReactStreamListener listener) {
        IntentAnalysisResult intent = analyzeIntent(userMessage, context, false);
        if (intent.isSimpleAnswer()) {
            String content = simpleAgent.respond(userMessage, context);
            List<ThinkingStep> steps = List.of(
                    ThinkingStep.thinking(getId(), getName(), "问题较简单，直接走快速回答路径。"),
                    ThinkingStep.observation(getId(), getName(), content)
            );
            ReactResult result = new ReactResult(steps, content, intent.rationale(), List.of());
            listener.onComplete(result);
            return result;
        }

        PlanningExecution execution = runPlannerSequential(
                userMessage,
                context,
                intent,
                decomposeTask(intent, context),
                listener,
                new ExecutionHistory(),
                MAX_PLANNED_TASKS
        );
        String finalContent = aggregateResults(intent, context, null, "", "", execution.taskResults(), execution.reports(), "");
        ReactResult result = new ReactResult(execution.steps(), finalContent, intent.rationale(), execution.reports());
        listener.onComplete(result);
        return result;
    }

    public IntentAnalysisResult analyzeIntent(String userMessage, ConversationContext context) {
        return analyzeIntent(userMessage, context, false);
    }

    public List<SubTask> decomposeTask(IntentAnalysisResult intent, ConversationContext context) {
        List<SubTask> tasks = new ArrayList<>();
        IntentType primary = intent.primaryIntent();
        tasks.add(createPrimaryTask(primary));
        if (intent.supports(IntentType.KNOWLEDGE_RETRIEVAL)
                && primary != IntentType.KNOWLEDGE_RETRIEVAL
                && !context.relevantNorms().isEmpty()) {
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

    private List<SubTask> buildInitialTasks(IntentAnalysisResult intent, String userMessage) {
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

    private boolean isParallelizable(IntentType intentType) {
        return intentType == IntentType.SECURITY_ANALYSIS
                || intentType == IntentType.PERFORMANCE_ANALYSIS
                || intentType == IntentType.ARCHITECTURE_ANALYSIS
                || intentType == IntentType.CODE_REVIEW;
    }

    private List<SpecialistReport> runParallelSpecialists(List<SubTask> tasks,
                                                        String userMessage,
                                                        ConversationContext context,
                                                        IntentAnalysisResult intent,
                                                        ReactStreamListener listener,
                                                        List<SpecialistExecutionResult> parallelResults,
                                                        List<TaskExecutionResult> allTaskResults,
                                                        List<ThinkingStep> allSteps) {
        List<SpecialistReport> reports = new ArrayList<>();
        if (tasks.isEmpty()) {
            return reports;
        }

        Map<String, SpecialistExecutionResult> futureResults = new LinkedHashMap<>();
        for (SubTask task : tasks) {
            SpecialistAgent specialist = resolveSpecialist(task, intent);
            if (specialist == null) {
                continue;
            }
            listener.onAgentStart(specialist.getId(), specialist.getName());

            CompletableFuture<SpecialistExecutionResult> future = CompletableFuture.supplyAsync(
                    () -> specialist.executeTask(userMessage, context, task, reactLoop, listener),
                    specialistExecutor
            );
            futureResults.put(specialist.getId(), null);
            future.thenAccept(result -> {
                synchronized (futureResults) {
                    futureResults.put(specialist.getId(), result);
                }
            });
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < PARALLEL_TIMEOUT_SECONDS * 1000L) {
            boolean allDone = true;
            synchronized (futureResults) {
                for (Map.Entry<String, SpecialistExecutionResult> entry : futureResults.entrySet()) {
                    if (entry.getValue() == null) {
                        allDone = false;
                        break;
                    }
                }
            }
            if (allDone) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        for (Map.Entry<String, SpecialistExecutionResult> entry : futureResults.entrySet()) {
            SpecialistExecutionResult execResult = entry.getValue();
            if (execResult != null) {
                parallelResults.add(execResult);
                allSteps.addAll(execResult.steps());
                SpecialistReport report = execResult.report();
                reports.add(report);
                listener.onAgentComplete(report);

                String agentId = entry.getKey();
                SubTask matchedTask = tasks.stream()
                        .filter(t -> {
                            SpecialistAgent a = resolveSpecialist(t, intent);
                            return a != null && a.getId().equals(agentId);
                        })
                        .findFirst()
                        .orElse(null);

                if (matchedTask != null) {
                    allTaskResults.add(new TaskExecutionResult(
                            matchedTask,
                            agentId,
                            execResult.report().agentName(),
                            true,
                            safe(execResult.report().summary()),
                            "",
                            ReflectionResult.ok(),
                            List.of(),
                            report
                    ));
                }
            }
        }

        return reports;
    }

    private PlanningExecution runPlannerSequential(String userMessage,
                                         ConversationContext context,
                                         IntentAnalysisResult intent,
                                         List<SubTask> initialTasks,
                                         ReactStreamListener listener,
                                         ExecutionHistory history,
                                         int maxTasks) {
        ArrayDeque<SubTask> queue = new ArrayDeque<>(initialTasks == null ? List.of() : initialTasks);
        Set<String> queuedTaskKeys = new LinkedHashSet<>();
        queue.forEach(task -> queuedTaskKeys.add(taskKey(task)));
        BacktrackingPlanner backtrackingPlanner = new BacktrackingPlanner();
        List<TaskExecutionResult> taskResults = new ArrayList<>();
        List<SpecialistReport> reports = new ArrayList<>();
        List<ThinkingStep> allSteps = new ArrayList<>();

        while (!queue.isEmpty() && taskResults.size() < Math.max(maxTasks, 0)) {
            SubTask task = queue.removeFirst();
            queuedTaskKeys.remove(taskKey(task));
            if (task.depth() > MAX_TASK_DEPTH) {
                continue;
            }
            if (history.isPathFailed(task) && !backtrackingPlanner.canRetry(task, history)) {
                continue;
            }

            SpecialistAgent specialist = resolveSpecialist(task, intent);
            if (specialist == null) {
                history.recordFailure(task, "planner", "No specialist can handle the task.");
                for (SubTask alternative : backtrackingPlanner.findAlternativePath(task, intent)) {
                    if (acceptProposal(alternative, queuedTaskKeys, history)) {
                        queue.addLast(alternative);
                        queuedTaskKeys.add(taskKey(alternative));
                    }
                }
                continue;
            }

            listener.onAgentStart(specialist.getId(), specialist.getName());
            try {
                SpecialistExecutionResult execution = specialist.executeTask(userMessage, context, task, reactLoop, listener);
                allSteps.addAll(execution.steps());
                SpecialistReport report = execution.report();
                reports.add(report);
                listener.onAgentComplete(report);

                ReflectionResult reflection = specialist.reflect(intent, task, report.summary(), context);
                List<SubTask> proposals = new ArrayList<>(specialist.proposeSubTasks(task, report.summary(), context));
                proposals.addAll(reflection.remediationTasks());

                taskResults.add(new TaskExecutionResult(
                        task,
                        specialist.getId(),
                        specialist.getName(),
                        true,
                        report.summary(),
                        "",
                        reflection,
                        proposals,
                        report
                ));
                history.recordSuccess(task, specialist.getId(), report.summary());
                for (SubTask proposal : proposals) {
                    if (acceptProposal(proposal, queuedTaskKeys, history)) {
                        queue.addLast(proposal);
                        queuedTaskKeys.add(taskKey(proposal));
                    }
                }
            } catch (Exception e) {
                String reason = safe(e.getMessage()).isBlank() ? "Unknown task failure" : safe(e.getMessage());
                history.recordFailure(task, specialist.getId(), reason);
                listener.onError(reason, e);
                List<SubTask> alternatives = backtrackingPlanner.findAlternativePath(task, intent);
                taskResults.add(new TaskExecutionResult(
                        task,
                        specialist.getId(),
                        specialist.getName(),
                        false,
                        "",
                        reason,
                        ReflectionResult.gap(reason, alternatives),
                        alternatives,
                        new SpecialistReport(specialist.getId(), specialist.getName(), reason)
                ));
                for (SubTask alternative : alternatives) {
                    if (acceptProposal(alternative, queuedTaskKeys, history)) {
                        queue.addLast(alternative);
                        queuedTaskKeys.add(taskKey(alternative));
                    }
                }
            }
        }
        return new PlanningExecution(taskResults, reports, allSteps);
    }

    private IntentAnalysisResult analyzeIntent(String userMessage, ConversationContext context, boolean forceReview) {
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
        if (containsAny(normalized, "document", "文档", "readme", "注释", "api 文档", "说明")) {
            intents.add(IntentType.DOCUMENTATION_GENERATION);
        }
        if (containsAny(normalized, "规范", "标准", "guideline", "best practice", "历史", "memory")) {
            intents.add(IntentType.KNOWLEDGE_RETRIEVAL);
        }
        if (intents.isEmpty()) {
            if (!forceReview && !context.hasRepositoryContext() && normalized.length() <= 18) {
                intents.add(IntentType.SIMPLE_ANSWER);
            } else {
                intents.add(IntentType.GENERAL_CODING);
            }
        }
        IntentType primaryIntent = intents.get(0);
        int complexityScore = Math.min(100,
                15 + intents.size() * 20 + (context.hasRepositoryContext() ? 15 : 0) + (context.relevantNorms().isEmpty() ? 0 : 10));
        boolean requiresRepositoryContext = forceReview
                || primaryIntent == IntentType.CODE_REVIEW
                || primaryIntent == IntentType.ARCHITECTURE_ANALYSIS
                || primaryIntent == IntentType.DOCUMENTATION_GENERATION
                || context.hasRepositoryContext();
        boolean requiresReflection = complexityScore >= 30 || intents.size() > 1;
        boolean requiresBacktracking = complexityScore >= 50 || intents.size() > 2;
        String rationale = "Intent=" + primaryIntent + ", candidates=" + intents + ", repositoryContext=" + context.hasRepositoryContext();
        return new IntentAnalysisResult(primaryIntent, intents, complexityScore, requiresRepositoryContext, requiresReflection, requiresBacktracking, rationale);
    }

    private SpecialistAgent resolveSpecialist(SubTask task, IntentAnalysisResult intent) {
        if (task == null) {
            return null;
        }
        if (task.preferredAgentId() != null && !task.preferredAgentId().isBlank()) {
            SpecialistAgent preferred = specialistRegistry.get(task.preferredAgentId());
            if (preferred != null && preferred.canHandle(task)) {
                return preferred;
            }
        }
        return specialistRegistry.values().stream()
                .filter(agent -> agent.canHandle(task))
                .findFirst()
                .orElseGet(() -> specialistRegistry.values().stream()
                        .filter(agent -> agent.canHandle(intent))
                        .findFirst()
                        .orElse(null));
    }

    private boolean acceptProposal(SubTask proposal, Set<String> queuedTaskKeys, ExecutionHistory history) {
        if (proposal == null || proposal.depth() > MAX_TASK_DEPTH) {
            return false;
        }
        if (history.isPathFailed(proposal)) {
            return false;
        }
        if (queuedTaskKeys.contains(taskKey(proposal))) {
            return false;
        }
        return proposal.preferredAgentId().isBlank() || specialistRegistry.containsKey(proposal.preferredAgentId());
    }

    private String aggregateResults(IntentAnalysisResult intent,
                                    ConversationContext context,
                                    List<TaskExecutionResult> taskResults,
                                    List<SpecialistReport> reports,
                                    String primarySummary) {
        if (reviewSpecialistAgent != null) {
            return aggregateResults(intent, context, null, "", "", taskResults, reports, primarySummary);
        }
        String reportText = reports.stream()
                .filter(Objects::nonNull)
                .map(item -> "[" + item.agentName() + "]\n" + safe(item.summary()))
                .collect(Collectors.joining("\n\n"));
        String taskText = taskResults.stream()
                .map(item -> "- " + item.task().title() + " => " + (item.success() ? "SUCCESS" : "FAILED")
                        + (item.failureReason().isBlank() ? "" : " (" + item.failureReason() + ")"))
                .collect(Collectors.joining("\n"));
        if (textGenerator.available()) {
            String input = """
                    [Primary Intent]
                    %s

                    [Planner Rationale]
                    %s

                    [Repository]
                    %s

                    [Primary Summary]
                    %s

                    [Specialist Reports]
                    %s

                    [Task Status]
                    %s

                    Merge these planning results into one final answer in Simplified Chinese.
                    Use the structure:
                    1. 结论摘要
                    2. 核心依据
                    3. 下一步建议
                    """.formatted(
                    intent.primaryIntent(),
                    intent.rationale(),
                    context.repositorySummary(),
                    safe(primarySummary),
                    reportText.isBlank() ? "N/A" : reportText,
                    taskText.isBlank() ? "N/A" : taskText
            );
            String output = textGenerator.generate(
                    getName(),
                    "You are the planner agent. Aggregate specialist results into one reliable final answer in Simplified Chinese.",
                    input,
                    Map.of(
                            "sessionId", safe(context.sessionId()),
                            "projectId", safe(context.projectId()),
                            "repoUrl", safe(context.repoUrl()),
                            "branch", safe(context.branch()),
                            "language", safe(context.language()),
                            "scene", "planner-aggregate",
                            "disableToolCallbacks", true
                    )
            );
            if (output != null && !output.isBlank()) {
                return output.trim();
            }
        }
        return fallbackAggregate(primarySummary, reportText, taskText);
    }

    private String fallbackAggregate(String primarySummary, String reportText, String taskText) {
        StringBuilder builder = new StringBuilder();
        builder.append("结论摘要:\n");
        builder.append(safe(primarySummary).isBlank() ? "- 已完成规划执行，但缺少可聚合的主结果。\n" : safe(primarySummary) + "\n");
        builder.append("\n核心依据:\n");
        builder.append(reportText == null || reportText.isBlank() ? "- 暂无额外 specialist 结果。\n" : reportText + "\n");
        builder.append("\n下一步建议:\n");
        builder.append(taskText == null || taskText.isBlank() ? "- 如需更深入分析，请补充更多仓库上下文或代码片段。" : taskText);
        return builder.toString();
    }

    private String aggregateResults(IntentAnalysisResult intent,
                                    ConversationContext context,
                                    ReviewReport reviewReport,
                                    String advisorOutput,
                                    String refactoredCode,
                                    List<TaskExecutionResult> taskResults,
                                    List<SpecialistReport> reports,
                                    String primarySummary) {
        String metricsText = formatMetrics(reviewReport);
        String issueText = formatIssues(reviewReport, 5);
        String suggestionText = formatSuggestions(reviewReport, 5);
        String refactorText = hasText(advisorOutput) ? advisorOutput.trim() : suggestionText;
        String refactoredCodePreview = formatRefactoredCode(refactoredCode, context.language());
        String reportText = reports.stream()
                .filter(Objects::nonNull)
                .map(item -> "[" + item.agentName() + "]\n" + safe(item.summary()))
                .collect(Collectors.joining("\n\n"));
        String taskText = taskResults.stream()
                .map(item -> "- " + item.task().title() + " => " + (item.success() ? "SUCCESS" : "FAILED")
                        + (item.failureReason().isBlank() ? "" : " (" + item.failureReason() + ")"))
                .collect(Collectors.joining("\n"));

        if (textGenerator.available()) {
            String input = """
                    [Primary Intent]
                    %s

                    [Planner Rationale]
                    %s

                    [Repository]
                    %s

                    [Primary Summary]
                    %s

                    [Review Metrics]
                    %s

                    [Top Issues]
                    %s

                    [Refactoring Advice]
                    %s

                    [Refactored Code Preview]
                    %s

                    [Specialist Reports]
                    %s

                    [Task Status]
                    %s

                    Merge these planning results into one complete review report in Simplified Chinese.
                    Use strict Markdown and include all of the following sections:
                    ## 综合结论
                    ## 评分与风险概览
                    ## 关键问题
                    ## 代码重构建议
                    ## 重构代码预览
                    ## 协同分析补充
                    ## 下一步建议

                    Requirements:
                    - Explicitly mention score and issue counts.
                    - Summarize the most important issues with file, severity, and suggestion when available.
                    - Include refactoring advice.
                    - If refactored code is available, include a fenced code block preview.
                    - If no extra specialist output exists, say so briefly instead of omitting the section.
                    """.formatted(
                    intent.primaryIntent(),
                    intent.rationale(),
                    context.repositorySummary(),
                    safe(primarySummary),
                    metricsText.isBlank() ? "N/A" : metricsText,
                    issueText.isBlank() ? "N/A" : issueText,
                    refactorText.isBlank() ? "N/A" : refactorText,
                    refactoredCodePreview.isBlank() ? "N/A" : refactoredCodePreview,
                    reportText.isBlank() ? "N/A" : reportText,
                    taskText.isBlank() ? "N/A" : taskText
            );
            String output = textGenerator.generate(
                    getName(),
                    "You are the planner agent. Aggregate specialist results into one reliable and complete review report in Simplified Chinese.",
                    input,
                    Map.of(
                            "sessionId", safe(context.sessionId()),
                            "projectId", safe(context.projectId()),
                            "repoUrl", safe(context.repoUrl()),
                            "branch", safe(context.branch()),
                            "language", safe(context.language()),
                            "scene", "planner-aggregate",
                            "disableToolCallbacks", true
                    )
            );
            if (output != null && !output.isBlank()) {
                return output.trim();
            }
        }

        return fallbackAggregate(primarySummary, metricsText, issueText, refactorText, refactoredCodePreview, reportText, taskText);
    }

    private String fallbackAggregate(String primarySummary,
                                     String metricsText,
                                     String issueText,
                                     String refactorText,
                                     String refactoredCodePreview,
                                     String reportText,
                                     String taskText) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 综合结论\n");
        builder.append(hasText(primarySummary) ? primarySummary.trim() : "已完成审查流程，但缺少可聚合的主结论。");
        builder.append("\n\n## 评分与风险概览\n");
        builder.append(hasText(metricsText) ? metricsText : "- 暂无评分与问题统计。");
        builder.append("\n\n## 关键问题\n");
        builder.append(hasText(issueText) ? issueText : "- 暂无可列出的关键问题。");
        builder.append("\n\n## 代码重构建议\n");
        builder.append(hasText(refactorText) ? refactorText : "- 暂无额外重构建议。");
        builder.append("\n\n## 重构代码预览\n");
        builder.append(hasText(refactoredCodePreview) ? refactoredCodePreview : "```text\n暂无重构代码预览\n```");
        builder.append("\n\n## 协同分析补充\n");
        builder.append(hasText(reportText) ? reportText : "- 暂无额外 specialist 补充结论。");
        builder.append("\n\n## 下一步建议\n");
        builder.append(hasText(taskText) ? taskText : "- 如需更深入分析，请补充更多仓库上下文或代码片段。");
        return builder.toString();
    }

    private String formatMetrics(ReviewReport reviewReport) {
        if (reviewReport == null) {
            return "";
        }
        return """
                - 评分: %s
                - 问题总数: %s
                - 严重问题: %s
                - 高风险问题: %s
                - 中风险问题: %s
                - 低风险问题: %s
                """.formatted(
                reviewReport.getScore(),
                reviewReport.getTotalIssues(),
                reviewReport.getCriticalCount(),
                reviewReport.getHighCount(),
                reviewReport.getMediumCount(),
                reviewReport.getLowCount()
        ).trim();
    }

    private String formatIssues(ReviewReport reviewReport, int limit) {
        if (reviewReport == null || reviewReport.getIssues() == null || reviewReport.getIssues().isEmpty()) {
            return "";
        }
        return reviewReport.getIssues().stream()
                .limit(Math.max(limit, 1))
                .map(this::formatIssue)
                .filter(this::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatIssue(ReviewIssue issue) {
        if (issue == null) {
            return "";
        }
        String location = hasText(issue.file()) ? issue.file() + (issue.lineNumber() > 0 ? ":" + issue.lineNumber() : "") : "未知位置";
        return """
                ### %s | %s
                - 位置: %s
                - 问题: %s
                - 建议: %s
                """.formatted(
                safe(issue.severity()),
                safe(issue.ruleId()),
                location,
                safe(issue.message()),
                safe(issue.suggestion())
        ).trim();
    }

    private String formatSuggestions(ReviewReport reviewReport, int limit) {
        if (reviewReport == null || reviewReport.getSuggestions() == null || reviewReport.getSuggestions().isEmpty()) {
            return "";
        }
        return reviewReport.getSuggestions().stream()
                .limit(Math.max(limit, 1))
                .map(this::formatSuggestion)
                .collect(Collectors.joining("\n"));
    }

    private String formatSuggestion(ReviewSuggestion suggestion) {
        if (suggestion == null) {
            return "";
        }
        return "- P" + suggestion.priority() + " " + safe(suggestion.title()) + ": " + safe(suggestion.description());
    }

    private String formatRefactoredCode(String refactoredCode, String language) {
        if (!hasText(refactoredCode)) {
            return "";
        }
        String normalizedLanguage = safe(language).trim().toLowerCase(Locale.ROOT);
        String preview = shortenText(refactoredCode.replace("\r\n", "\n"), 2200);
        return "```" + normalizedLanguage + "\n" + preview + "\n```";
    }

    private String shortenText(String text, int maxLength) {
        String normalized = safe(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n// ... truncated";
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private ReactStreamListener createBridgeListener(String sessionId, AgentEventListener listener) {
        return new ReactStreamListener() {
            @Override
            public void onAgentStart(String agentId, String agentName) {
                listener.onEvent(sessionId, "agent_start", Map.of("agentId", safe(agentId), "name", safe(agentName)));
            }

            @Override
            public void onStep(ThinkingStep step) {
                if ("TOOL_CALL".equals(step.type())) {
                    listener.onEvent(sessionId, "tool_call", Map.of(
                            "agentId", safe(step.agentId()),
                            "agentName", safe(step.agentName()),
                            "tool", safe(step.toolName()),
                            "input", safe(step.input()),
                            "output", safe(step.output())
                    ));
                    return;
                }
                listener.onEvent(sessionId, "thinking", Map.of(
                        "agentId", safe(step.agentId()),
                        "agentName", safe(step.agentName()),
                        "type", safe(step.type()),
                        "content", safe(step.content())
                ));
            }

            @Override
            public void onAgentComplete(SpecialistReport report) {
                listener.onEvent(sessionId, "agent_complete", Map.of(
                        "agentId", safe(report.agentId()),
                        "name", safe(report.agentName()),
                        "content", safe(report.summary())
                ));
            }

            @Override
            public void onError(String message, Throwable throwable) {
                listener.onEvent(sessionId, "error", Map.of("message", safe(message)));
            }
        };
    }

    private ConversationContext toConversationContext(ReviewContext context) {
        List<String> messages = context.recentMessages() == null
                ? List.of()
                : context.recentMessages().stream()
                .map(item -> item.role() + ": " + item.content())
                .toList();
        List<String> histories = context.historicalReviews() == null ? List.of() : List.copyOf(context.historicalReviews());
        return new ConversationContext(
                context.sessionId(),
                safe(context.projectId()),
                safe(context.repoUrl()),
                safe(context.branch()),
                safe(context.language()),
                safe(context.codeContent()),
                safe(context.codeContent()),
                messages,
                List.of(),
                histories,
                List.of()
        );
    }

    private String buildReviewRequest(ReviewContext context) {
        return "请对当前代码变更做结构化代码审查，语言=" + safe(context.language())
                + "，reviewId=" + safe(context.reviewId())
                + "，重点关注正确性、可维护性、安全与性能。";
    }

    private SubTask createPrimaryTask(IntentType intentType) {
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

    private String taskKey(SubTask task) {
        return task.intentType() + ":" + safe(task.title()) + ":" + safe(task.preferredAgentId());
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private record PlanningExecution(
            List<TaskExecutionResult> taskResults,
            List<SpecialistReport> reports,
            List<ThinkingStep> steps
    ) {
    }
}
