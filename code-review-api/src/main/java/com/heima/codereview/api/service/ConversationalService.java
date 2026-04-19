package com.heima.codereview.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.codereview.common.exception.BizException;
import com.heima.codereview.common.model.chat.ChatHistoryRecord;
import com.heima.codereview.common.model.chat.ChatMessage;
import com.heima.codereview.common.model.chat.ChatReply;
import com.heima.codereview.common.model.chat.ChatSendRequest;
import com.heima.codereview.common.model.chat.ReactChatRequest;
import com.heima.codereview.common.model.norm.NormRecord;
import com.heima.codereview.common.model.norm.NormSummary;
import com.heima.codereview.common.model.norm.NormUploadResult;
import com.heima.codereview.common.persistence.entity.ReviewTaskDO;
import com.heima.codereview.common.persistence.mapper.ReviewTaskMapper;
import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.ReactStreamListener;
import com.heima.codereview.core.agent.conversational.ReActAgent;
import com.heima.codereview.core.agent.conversational.SimpleAgent;
import com.heima.codereview.core.agent.conversational.SpecialistReport;
import com.heima.codereview.core.agent.react.ReactResult;
import com.heima.codereview.core.agent.react.ThinkingStep;
import com.heima.codereview.core.memory.ChatMemory;
import com.heima.codereview.core.memory.SessionCodeContext;
import com.heima.codereview.core.memory.SessionCodeContextMemory;
import com.heima.codereview.rag.ChatHistoryRepository;
import com.heima.codereview.rag.PdfRepository;
import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.retrieval.ChatRetrieval;
import com.heima.codereview.tools.git.GitDiffFetcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationalService {

    private static final Pattern HTTP_REPO_URL_PATTERN = Pattern.compile("(https?://[^\\s,，;；]+)");
    private static final Pattern SSH_REPO_URL_PATTERN = Pattern.compile("(git@[^\\s,，;；]+)");
    private static final Pattern WINDOWS_REPO_PATH_PATTERN = Pattern.compile("([A-Za-z]:\\\\[^\\s,，;；]+)");
    private static final Pattern EXPLICIT_BRANCH_PATTERN = Pattern.compile("(?i)branch\\s*[:：]?\\s*([A-Za-z0-9._/-]+)");
    private static final Pattern BRANCH_PATTERN = Pattern.compile("(?i)([A-Za-z0-9._/-]+)\\s*分支");
    private static final Pattern EXPLICIT_LANGUAGE_PATTERN = Pattern.compile("(?i)language\\s*[:：]?\\s*([A-Za-z+#._-]+)");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("(?i)([A-Za-z+#._-]+)\\s*语言");
    private static final int REPOSITORY_CONTEXT_MAX_LENGTH = 6000;

    private final ChatMemory chatMemory;
    private final ChatRetrieval chatRetrieval;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ChatHistoryRepository chatHistoryRepository;
    private final PdfRepository pdfRepository;
    private final GitDiffFetcher gitDiffFetcher;
    private final SimpleAgent simpleAgent;
    private final ReActAgent reActAgent;
    private final SessionCodeContextMemory sessionCodeContextMemory;
    private final Executor reviewTaskExecutor;

    public ConversationalService(ChatMemory chatMemory,
                                 ChatRetrieval chatRetrieval,
                                 ReviewTaskMapper reviewTaskMapper,
                                 ChatHistoryRepository chatHistoryRepository,
                                 PdfRepository pdfRepository,
                                 GitDiffFetcher gitDiffFetcher,
                                 SimpleAgent simpleAgent,
                                 ReActAgent reActAgent,
                                 SessionCodeContextMemory sessionCodeContextMemory,
                                 @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor) {
        this.chatMemory = chatMemory;
        this.chatRetrieval = chatRetrieval;
        this.reviewTaskMapper = reviewTaskMapper;
        this.chatHistoryRepository = chatHistoryRepository;
        this.pdfRepository = pdfRepository;
        this.gitDiffFetcher = gitDiffFetcher;
        this.simpleAgent = simpleAgent;
        this.reActAgent = reActAgent;
        this.sessionCodeContextMemory = sessionCodeContextMemory;
        this.reviewTaskExecutor = reviewTaskExecutor;
    }

    public ChatReply send(ChatSendRequest request) {
        String question = normalizeMessage(request.message());
        SessionInfo sessionInfo = resolveSessionInfo(request.sessionId(), request.projectId());
        RepositoryContext repositoryContext = resolveRepositoryContext(sessionInfo, question);

        persistMessage(sessionInfo.sessionId(), new ChatMessage("user", question, System.currentTimeMillis()));
        ContextBundle bundle = buildContextBundle(sessionInfo.sessionId(), sessionInfo.projectId(), question, repositoryContext);
        String answer = simpleAgent.respond(question, bundle.context());
        persistMessage(sessionInfo.sessionId(), new ChatMessage("assistant", answer, System.currentTimeMillis()));

        return new ChatReply(sessionInfo.sessionId(), answer, bundle.referenceIds());
    }

    public SseEmitter react(ReactChatRequest request) {
        String question = normalizeMessage(request.message());
        SessionInfo sessionInfo = resolveSessionInfo(request.sessionId(), request.projectId());
        RepositoryContext repositoryContext = resolveRepositoryContext(sessionInfo, question);
        SseEmitter emitter = new SseEmitter(0L);

        reviewTaskExecutor.execute(() -> {
            try {
                sendEvent(emitter, "connected", Map.of(
                        "sessionId", sessionInfo.sessionId(),
                        "projectId", sessionInfo.projectId()
                ));
                persistMessage(sessionInfo.sessionId(), new ChatMessage("user", question, System.currentTimeMillis()));
                ContextBundle bundle = buildContextBundle(sessionInfo.sessionId(), sessionInfo.projectId(), question, repositoryContext);
                SseReactStreamListener listener = new SseReactStreamListener(emitter);
                ReactResult result = reActAgent.analyze(question, bundle.context(), listener);
                persistMessage(sessionInfo.sessionId(), new ChatMessage("assistant", result.finalContent(), System.currentTimeMillis()));
                sendEvent(emitter, "done", Map.of(
                        "sessionId", sessionInfo.sessionId(),
                        "projectId", sessionInfo.projectId(),
                        "content", safe(result.finalContent()),
                        "summary", safe(result.summary())
                ));
                emitter.complete();
            } catch (Exception e) {
                sendEvent(emitter, "error", Map.of("message", e.getMessage() == null ? "深度分析失败" : e.getMessage()));
                emitter.complete();
            }
        });

        return emitter;
    }

    public NormUploadResult uploadNorm(MultipartFile file, String projectId, String description) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请上传有效的 PDF 规范文档。");
        }
        try {
            return pdfRepository.uploadPdf(file.getInputStream(), file.getOriginalFilename(), Map.of(
                    "projectId", safe(projectId),
                    "description", safe(description)
            ));
        } catch (IOException e) {
            throw new BizException("读取上传文件失败: " + e.getMessage());
        }
    }

    public List<NormRecord> searchNorms(String query, String projectId, int limit) {
        return pdfRepository.searchNorms(query, projectId, limit);
    }

    public List<NormSummary> listNorms(String projectId) {
        return pdfRepository.listNorms(projectId);
    }

    private ContextBundle buildContextBundle(String sessionId, String projectId, String question, RepositoryContext repositoryContext) {
        List<ReviewRecord> reviewRecords = chatRetrieval.retrieve(question, projectId, sessionId, 4);
        List<ChatHistoryRecord> histories = chatHistoryRepository.searchRelevant(question, sessionId, 4);
        List<NormRecord> norms = pdfRepository.searchNorms(question, projectId, 4);

        ConversationContext context = new ConversationContext(
                sessionId,
                projectId,
                repositoryContext.repoUrl(),
                repositoryContext.branch(),
                repositoryContext.language(),
                preview(repositoryContext.diff(), REPOSITORY_CONTEXT_MAX_LENGTH),
                repositoryContext.diff(),
                formatRecentMessages(chatMemory.recent(sessionId)),
                formatHistories(histories),
                formatReviewRecords(reviewRecords),
                formatNorms(norms)
        );

        List<String> referenceIds = reviewRecords.stream()
                .map(ReviewRecord::reviewId)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        return new ContextBundle(context, referenceIds);
    }

    private void persistMessage(String sessionId, ChatMessage message) {
        chatMemory.append(sessionId, message);
        chatHistoryRepository.saveChatHistory(sessionId, message);
    }

    private SessionInfo resolveSessionInfo(String sessionId, String requestedProjectId) {
        String realSessionId = sessionId == null || sessionId.isBlank()
                ? com.heima.codereview.common.utils.IdUtils.withPrefix("session")
                : sessionId;
        String projectId = requestedProjectId == null || requestedProjectId.isBlank()
                ? resolveProjectId(realSessionId)
                : requestedProjectId.trim();
        return new SessionInfo(realSessionId, projectId);
    }

    private String resolveProjectId(String sessionId) {
        ReviewTaskDO latestTask = reviewTaskMapper.selectOne(new LambdaQueryWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getSessionId, sessionId)
                .orderByDesc(ReviewTaskDO::getUpdatedAt)
                .orderByDesc(ReviewTaskDO::getCreatedAt)
                .last("limit 1"));
        if (latestTask == null || latestTask.getProjectId() == null) {
            return "";
        }
        return latestTask.getProjectId();
    }

    private RepositoryContext resolveRepositoryContext(SessionInfo sessionInfo, String question) {
        RepositoryContext currentContext = resolveRepositoryContextFromText(question);
        if (currentContext.hasUsableContent()) {
            rememberRepositoryContext(sessionInfo, currentContext);
            return currentContext;
        }

        if (!shouldReuseRepositoryContext(question)) {
            return RepositoryContext.empty();
        }

        return sessionCodeContextMemory.find(sessionInfo.sessionId())
                .filter(SessionCodeContext::hasContent)
                .map(this::toRepositoryContext)
                .orElseGet(() -> {
                    RepositoryContext recentContext = resolveRepositoryContextFromRecentMessages(sessionInfo);
                    if (recentContext.hasUsableContent()) {
                        rememberRepositoryContext(sessionInfo, recentContext);
                    }
                    return recentContext;
                });
    }

    private RepositoryContext resolveRepositoryContextFromText(String question) {
        String repoUrl = extractFirst(question, HTTP_REPO_URL_PATTERN, SSH_REPO_URL_PATTERN, WINDOWS_REPO_PATH_PATTERN);
        if (repoUrl.isBlank()) {
            return RepositoryContext.empty();
        }
        String branch = extractFirst(question, EXPLICIT_BRANCH_PATTERN, BRANCH_PATTERN);
        String language = extractFirst(question, EXPLICIT_LANGUAGE_PATTERN, LANGUAGE_PATTERN);
        String diff = gitDiffFetcher.fetchDiff(repoUrl, branch, language);
        return new RepositoryContext(
                repoUrl,
                branch,
                language,
                diff
        );
    }

    private RepositoryContext resolveRepositoryContextFromRecentMessages(SessionInfo sessionInfo) {
        List<ChatMessage> recentMessages = chatMemory.recent(sessionInfo.sessionId());
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = recentMessages.get(index);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            RepositoryContext repositoryContext = resolveRepositoryContextFromText(message.content());
            if (repositoryContext.hasUsableContent()) {
                return repositoryContext;
            }
        }
        return RepositoryContext.empty();
    }

    private void rememberRepositoryContext(SessionInfo sessionInfo, RepositoryContext repositoryContext) {
        sessionCodeContextMemory.remember(sessionInfo.sessionId(), new SessionCodeContext(
                sessionInfo.projectId(),
                repositoryContext.repoUrl(),
                repositoryContext.branch(),
                repositoryContext.language(),
                repositoryContext.diff(),
                System.currentTimeMillis()
        ));
    }

    private RepositoryContext toRepositoryContext(SessionCodeContext context) {
        return new RepositoryContext(
                safe(context.repoUrl()),
                safe(context.branch()),
                safe(context.language()),
                context.content()  // 不再截断，保留完整代码
        );
    }

    private boolean shouldReuseRepositoryContext(String question) {
        String normalized = safe(question).toLowerCase();
        return normalized.length() <= 24
                || containsAny(normalized, "审查", "review", "代码", "提交", "差异", "diff", "仓库", "继续", "刚才", "前面", "最近");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String extractFirst(String source, Pattern... patterns) {
        String text = safe(source);
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) {
                continue;
            }
            for (int groupIndex = 1; groupIndex <= matcher.groupCount(); groupIndex++) {
                String value = matcher.group(groupIndex);
                if (value != null && !value.isBlank()) {
                    return sanitizeExtractedValue(value);
                }
            }
            String value = matcher.group();
            if (value != null && !value.isBlank()) {
                return sanitizeExtractedValue(value);
            }
        }
        return "";
    }

    private String sanitizeExtractedValue(String value) {
        String sanitized = safe(value).trim();
        while (!sanitized.isEmpty() && ",，;；。".indexOf(sanitized.charAt(sanitized.length() - 1)) >= 0) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        }
        return sanitized;
    }

    private List<String> formatRecentMessages(List<ChatMessage> messages) {
        return messages.stream()
                .limit(8)
                .map(item -> item.role() + ": " + preview(item.content(), 160))
                .toList();
    }

    private List<String> formatHistories(List<ChatHistoryRecord> histories) {
        return histories.stream()
                .map(item -> "[" + item.sessionId() + "] " + preview(item.content(), 180))
                .toList();
    }

    private List<String> formatReviewRecords(List<ReviewRecord> reviewRecords) {
        return reviewRecords.stream()
                .map(item -> "reviewId=" + safe(item.reviewId()) + " | " + preview(item.content(), 180))
                .toList();
    }

    private List<String> formatNorms(List<NormRecord> norms) {
        return norms.stream()
                .map(item -> item.fileName() + " 第" + item.pageNumber() + " 页 | " + preview(item.summary(), 180))
                .toList();
    }

    private String normalizeMessage(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank()) {
            throw new BizException("请输入有效的问题内容");
        }
        return normalized;
    }

    private String preview(String text, int maxLength) {
        String normalized = safe(text).replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException ignored) {
        }
    }

    private record SessionInfo(String sessionId, String projectId) {
    }

    private record ContextBundle(ConversationContext context, List<String> referenceIds) {
    }

    private record RepositoryContext(String repoUrl, String branch, String language, String diff) {

        private static RepositoryContext empty() {
            return new RepositoryContext("", "", "", "");
        }

        private boolean hasUsableContent() {
            return diff != null
                    && !diff.isBlank()
                    && !diff.startsWith("未获取到可用的仓库差异");
        }
    }

    private static final class SseReactStreamListener implements ReactStreamListener {

        private final SseEmitter emitter;
        private final AtomicInteger stepCounter = new AtomicInteger();

        private SseReactStreamListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onAgentStart(String agentId, String agentName) {
            send("agent_start", Map.of(
                    "agentId", safe(agentId),
                    "name", safe(agentName)
            ));
        }

        @Override
        public void onStep(ThinkingStep step) {
            if ("TOOL_CALL".equals(step.type())) {
                send("tool_call", Map.of(
                        "step", stepCounter.incrementAndGet(),
                        "agentId", safe(step.agentId()),
                        "agentName", safe(step.agentName()),
                        "tool", safe(step.toolName()),
                        "input", safe(step.input()),
                        "output", safe(step.output())
                ));
                return;
            }
            send("thinking", Map.of(
                    "step", stepCounter.incrementAndGet(),
                    "agentId", safe(step.agentId()),
                    "agentName", safe(step.agentName()),
                    "type", safe(step.type()),
                    "content", safe(step.content())
            ));
        }

        @Override
        public void onAgentComplete(SpecialistReport report) {
            send("agent_complete", Map.of(
                    "agentId", safe(report.agentId()),
                    "name", safe(report.agentName()),
                    "content", safe(report.summary())
            ));
        }

        @Override
        public void onComplete(ReactResult result) {
        }

        @Override
        public void onError(String message, Throwable throwable) {
            send("error", Map.of("message", safe(message)));
        }

        private void send(String eventName, Object payload) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException ignored) {
            }
        }

        private static String safe(String text) {
            return text == null ? "" : text;
        }
    }
}
