package com.heima.codereview.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.codereview.api.persistence.ReviewTaskPersistenceRepository;
import com.heima.codereview.common.model.chat.ChatMessage;
import com.heima.codereview.common.model.chat.ChatReply;
import com.heima.codereview.common.model.chat.ChatSendRequest;
import com.heima.codereview.common.model.history.HistoryRecord;
import com.heima.codereview.common.model.history.HistorySearchResponse;
import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import com.heima.codereview.common.model.review.ReviewTaskDetail;
import com.heima.codereview.common.model.session.SessionSummary;
import com.heima.codereview.common.persistence.entity.ChatMessageDO;
import com.heima.codereview.common.persistence.entity.ReviewHistoryDO;
import com.heima.codereview.common.persistence.entity.ReviewTaskDO;
import com.heima.codereview.common.persistence.mapper.ChatMessageMapper;
import com.heima.codereview.common.persistence.mapper.ReviewHistoryMapper;
import com.heima.codereview.common.persistence.mapper.ReviewTaskMapper;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.agent.AgentTextGenerator;
import com.heima.codereview.core.memory.ChatMemory;
import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.retrieval.ChatRetrieval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String CHAT_AGENT_NAME = "对话Agent";
    private static final String CHAT_AGENT_INSTRUCTION = """
            你是智能代码审查与重构助手的对话Agent。
            你的所有回复都必须基于用户问题、会话上下文、最近审查结果和检索到的历史审查记录生成，禁止输出模板化敷衍回复。
            回复要求：
            1. 全部使用中文。
            2. 优先回答与代码审查结果、问题原因、修复建议、重构方案相关的问题。
            3. 如果引用了历史审查或最近审查结果，要自然说明依据。
            4. 如果上下文不足，明确说明缺少什么信息。
            5. 不要编造不存在的代码、执行结果或数据库记录。
            """;
    private static final int SESSION_PREVIEW_MAX = 120;
    private static final int SESSION_MESSAGE_LIMIT = 200;
    private static final int HISTORY_LIMIT = 200;
    private static final int RELATED_REVIEW_LIMIT = 3;
    private static final int REVIEW_TEXT_PREVIEW_MAX = 220;

    private final ChatMemory chatMemory;
    private final ChatRetrieval chatRetrieval;
    private final AgentTextGenerator agentTextGenerator;
    private final ChatMessageMapper chatMessageMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewHistoryMapper reviewHistoryMapper;
    private final ReviewTaskPersistenceRepository reviewTaskPersistenceRepository;

    public ChatService(ChatMemory chatMemory,
                       ChatRetrieval chatRetrieval,
                       AgentTextGenerator agentTextGenerator,
                       ChatMessageMapper chatMessageMapper,
                       ReviewTaskMapper reviewTaskMapper,
                       ReviewHistoryMapper reviewHistoryMapper,
                       ReviewTaskPersistenceRepository reviewTaskPersistenceRepository) {
        this.chatMemory = chatMemory;
        this.chatRetrieval = chatRetrieval;
        this.agentTextGenerator = agentTextGenerator;
        this.chatMessageMapper = chatMessageMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewHistoryMapper = reviewHistoryMapper;
        this.reviewTaskPersistenceRepository = reviewTaskPersistenceRepository;
    }

    public ChatReply send(ChatSendRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? IdUtils.withPrefix("session")
                : request.sessionId();
        String projectId = resolveProjectId(sessionId, request.projectId());
        long start = System.currentTimeMillis();

        chatMemory.append(sessionId, new ChatMessage("user", defaultText(request.message()), System.currentTimeMillis()));

        List<ReviewRecord> refs = chatRetrieval.retrieve(defaultText(request.message()), projectId, sessionId);
        List<ReviewTaskDetail> relatedReviews = loadRelatedReviews(sessionId, projectId, refs);
        String agentInput = buildAgentInput(sessionId, projectId, request, refs, relatedReviews);
        log.info("收到对话请求。sessionId={}, projectId={}, questionLength={}, refCount={}, relatedReviewCount={}",
                sessionId,
                projectId,
                request.message() == null ? 0 : request.message().length(),
                refs.size(),
                relatedReviews.size());

        String answer = agentTextGenerator.generate(
                CHAT_AGENT_NAME,
                CHAT_AGENT_INSTRUCTION,
                agentInput,
                Map.of(
                        "sessionId", sessionId,
                        "projectId", projectId,
                        "scene", "chat"
                )
        );
        if (answer == null || answer.isBlank()) {
            answer = "当前对话Agent暂时没有返回有效结果，请补充更具体的问题、代码片段或报错信息后再试。";
            log.warn("对话Agent返回空结果，已使用兜底提示。sessionId={}", sessionId);
        }

        chatMemory.append(sessionId, new ChatMessage("assistant", answer, System.currentTimeMillis()));
        log.info("对话Agent回复完成。sessionId={}, costMs={}, answerLength={}, refCount={}, relatedReviewCount={}",
                sessionId,
                System.currentTimeMillis() - start,
                answer.length(),
                refs.size(),
                relatedReviews.size());
        return new ChatReply(
                sessionId,
                answer,
                Stream.concat(refs.stream().map(ReviewRecord::reviewId), relatedReviews.stream().map(ReviewTaskDetail::getReviewId))
                        .filter(this::hasText)
                        .distinct()
                        .toList()
        );
    }

    public List<SessionSummary> listSessions() {
        Map<String, SessionAccumulator> sessions = new HashMap<>();

        List<ChatMessageDO> messageRows = loadSessionMessages();
        for (ChatMessageDO row : messageRows) {
            if (row.getSessionId() == null || row.getSessionId().isBlank()) {
                continue;
            }
            SessionAccumulator acc = sessions.computeIfAbsent(row.getSessionId(), SessionAccumulator::new);
            long ts = toEpochMilli(row.getCreatedAt());
            acc.messageCount++;
            acc.lastActivity = Math.max(acc.lastActivity, ts);
            if (ts >= acc.latestMessageTime) {
                acc.latestMessageTime = ts;
                acc.latestMessagePreview = preview(row.getContent());
            }
        }

        List<ReviewTaskDO> taskRows = loadSessionTasks();
        for (ReviewTaskDO row : taskRows) {
            if (row.getSessionId() == null || row.getSessionId().isBlank()) {
                continue;
            }
            SessionAccumulator acc = sessions.computeIfAbsent(row.getSessionId(), SessionAccumulator::new);
            long ts = toEpochMilli(firstNonNull(row.getUpdatedAt(), row.getCompletedAt(), row.getStartedAt(), row.getCreatedAt()));
            acc.reviewCount++;
            acc.lastActivity = Math.max(acc.lastActivity, ts);
            if (ts >= acc.latestTaskTime) {
                acc.latestTaskTime = ts;
                acc.projectId = defaultText(row.getProjectId());
                acc.latestReviewId = defaultText(row.getReviewId());
                acc.latestReviewStatus = defaultText(row.getStatus());
                acc.language = defaultText(row.getLanguage());
                if (acc.latestMessagePreview == null || acc.latestMessagePreview.isBlank()) {
                    acc.latestMessagePreview = preview(row.getSummary());
                }
            }
        }

        return sessions.values().stream()
                .sorted(Comparator.comparingLong(SessionAccumulator::lastActivity).reversed())
                .map(acc -> new SessionSummary(
                        acc.sessionId,
                        acc.projectId,
                        acc.latestReviewId,
                        acc.latestReviewStatus,
                        acc.language,
                        defaultText(acc.latestMessagePreview),
                        acc.lastActivity,
                        acc.messageCount,
                        acc.reviewCount
                ))
                .toList();
    }

    private List<ChatMessageDO> loadSessionMessages() {
        try {
            return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                    .select(ChatMessageDO::getSessionId,
                            ChatMessageDO::getRole,
                            ChatMessageDO::getContent,
                            ChatMessageDO::getCreatedAt)
                    .orderByDesc(ChatMessageDO::getCreatedAt));
        } catch (Exception e) {
            log.error("加载会话消息列表失败，已降级为空结果。reason={}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<ReviewTaskDO> loadSessionTasks() {
        try {
            return reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTaskDO>()
                    .select(ReviewTaskDO::getReviewId,
                            ReviewTaskDO::getSessionId,
                            ReviewTaskDO::getProjectId,
                            ReviewTaskDO::getLanguage,
                            ReviewTaskDO::getStatus,
                            ReviewTaskDO::getSummary,
                            ReviewTaskDO::getStartedAt,
                            ReviewTaskDO::getCompletedAt,
                            ReviewTaskDO::getCreatedAt,
                            ReviewTaskDO::getUpdatedAt)
                    .orderByDesc(ReviewTaskDO::getUpdatedAt)
                    .orderByDesc(ReviewTaskDO::getCreatedAt));
        } catch (Exception e) {
            log.error("加载审查任务列表失败，已降级为空结果。reason={}", e.getMessage(), e);
            return List.of();
        }
    }

    public List<ChatMessage> listMessages(String sessionId) {
        List<ChatMessageDO> rows = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getSessionId, sessionId)
                .orderByAsc(ChatMessageDO::getCreatedAt)
                .last("limit " + SESSION_MESSAGE_LIMIT));
        List<ChatMessage> messages = new ArrayList<>(rows.size());
        for (ChatMessageDO row : rows) {
            messages.add(new ChatMessage(defaultText(row.getRole()), defaultText(row.getContent()), toEpochMilli(row.getCreatedAt())));
        }
        return messages;
    }

    public HistorySearchResponse searchHistory(String keyword, String sessionId, String projectId) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        List<HistoryRecord> records = new ArrayList<>();

        LambdaQueryWrapper<ChatMessageDO> chatQuery = new LambdaQueryWrapper<ChatMessageDO>()
                .orderByDesc(ChatMessageDO::getCreatedAt)
                .last("limit " + HISTORY_LIMIT);
        if (hasText(sessionId)) {
            chatQuery.eq(ChatMessageDO::getSessionId, sessionId);
        }
        if (hasText(trimmedKeyword)) {
            chatQuery.like(ChatMessageDO::getContent, trimmedKeyword);
        }
        for (ChatMessageDO row : chatMessageMapper.selectList(chatQuery)) {
            records.add(new HistoryRecord(
                    defaultText(row.getMessageId()),
                    defaultText(row.getSessionId()),
                    "[对话] " + preview(row.getContent()),
                    toEpochMilli(row.getCreatedAt())
            ));
        }

        LambdaQueryWrapper<ReviewHistoryDO> historyQuery = new LambdaQueryWrapper<ReviewHistoryDO>()
                .orderByDesc(ReviewHistoryDO::getCreatedAt)
                .last("limit " + HISTORY_LIMIT);
        if (hasText(sessionId)) {
            historyQuery.eq(ReviewHistoryDO::getSessionId, sessionId);
        }
        if (hasText(projectId)) {
            historyQuery.eq(ReviewHistoryDO::getProjectId, projectId);
        }
        if (hasText(trimmedKeyword)) {
            historyQuery.like(ReviewHistoryDO::getSummary, trimmedKeyword);
        }
        for (ReviewHistoryDO row : reviewHistoryMapper.selectList(historyQuery)) {
            records.add(new HistoryRecord(
                    defaultText(row.getRecordId()),
                    defaultText(row.getSessionId()),
                    "[审查] " + preview(row.getSummary()),
                    toEpochMilli(row.getCreatedAt())
            ));
        }

        List<HistoryRecord> result = records.stream()
                .sorted(Comparator.comparingLong(HistoryRecord::timestamp).reversed())
                .limit(HISTORY_LIMIT)
                .toList();
        return new HistorySearchResponse(trimmedKeyword, result);
    }

    private String buildAgentInput(String sessionId,
                                   String projectId,
                                   ChatSendRequest request,
                                   List<ReviewRecord> refs,
                                   List<ReviewTaskDetail> relatedReviews) {
        List<ChatMessage> recentMessages = chatMemory.recent(sessionId);
        String historyText = recentMessages.stream()
                .limit(10)
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));

        String referenceText = refs.isEmpty()
                ? "无"
                : refs.stream()
                .map(record -> "recordId=" + record.id()
                        + "，reviewId=" + record.reviewId()
                        + "，摘要=" + preview(record.content(), REVIEW_TEXT_PREVIEW_MAX))
                .collect(Collectors.joining("\n"));

        String recentReviewText = relatedReviews.isEmpty()
                ? "无"
                : relatedReviews.stream()
                .map(this::formatReviewDetail)
                .collect(Collectors.joining("\n\n"));

        return """
                【项目ID】
                %s

                【当前会话ID】
                %s

                【用户问题】
                %s

                【最近会话记录】
                %s

                【检索命中的历史审查】
                %s

                【最近可参考的审查结果】
                %s

                请优先结合当前会话和最近审查结果回答，如果用户追问“上次审查结果”“这个会话之前审查了什么”“有哪些问题/建议”，需要直接引用上面的审查上下文回答。
                """.formatted(
                projectId,
                sessionId,
                defaultText(request.message()),
                historyText.isBlank() ? "无" : historyText,
                referenceText,
                recentReviewText
        );
    }

    private List<ReviewTaskDetail> loadRelatedReviews(String sessionId, String projectId, List<ReviewRecord> refs) {
        LinkedHashSet<String> reviewIds = new LinkedHashSet<>();
        for (ReviewRecord ref : refs) {
            if (hasText(ref.reviewId())) {
                reviewIds.add(ref.reviewId());
            }
        }

        if (reviewIds.size() < RELATED_REVIEW_LIMIT) {
            List<ReviewRecord> latestRecords = chatRetrieval.latest(projectId, sessionId, RELATED_REVIEW_LIMIT);
            for (ReviewRecord record : latestRecords) {
                if (hasText(record.reviewId())) {
                    reviewIds.add(record.reviewId());
                }
            }
        }

        if (reviewIds.size() < RELATED_REVIEW_LIMIT) {
            LambdaQueryWrapper<ReviewTaskDO> wrapper = new LambdaQueryWrapper<ReviewTaskDO>()
                    .orderByDesc(ReviewTaskDO::getCompletedAt)
                    .orderByDesc(ReviewTaskDO::getUpdatedAt)
                    .orderByDesc(ReviewTaskDO::getCreatedAt)
                    .last("limit " + RELATED_REVIEW_LIMIT);
            if (hasText(sessionId)) {
                wrapper.eq(ReviewTaskDO::getSessionId, sessionId);
            } else if (hasText(projectId)) {
                wrapper.eq(ReviewTaskDO::getProjectId, projectId);
            }
            if (!reviewIds.isEmpty()) {
                wrapper.notIn(ReviewTaskDO::getReviewId, reviewIds);
            }
            for (ReviewTaskDO row : reviewTaskMapper.selectList(wrapper)) {
                if (hasText(row.getReviewId())) {
                    reviewIds.add(row.getReviewId());
                }
            }
        }

        List<ReviewTaskDetail> details = new ArrayList<>();
        for (String reviewId : reviewIds) {
            if (details.size() >= RELATED_REVIEW_LIMIT) {
                break;
            }
            try {
                details.add(reviewTaskPersistenceRepository.findByReviewId(reviewId));
            } catch (Exception e) {
                log.warn("加载审查详情失败，已跳过。reviewId={}, reason={}", reviewId, e.getMessage());
            }
        }
        return details;
    }

    private String formatReviewDetail(ReviewTaskDetail detail) {
        ReviewReport report = detail.getReport();
        List<ReviewIssue> issues = report == null || report.getIssues() == null ? List.of() : report.getIssues();
        List<ReviewSuggestion> suggestions = report == null || report.getSuggestions() == null ? List.of() : report.getSuggestions();
        String issueText = issues.isEmpty()
                ? "无"
                : issues.stream()
                .limit(3)
                .map(issue -> defaultText(issue.file()) + ":" + issue.lineNumber() + " - " + preview(issue.message(), 80))
                .collect(Collectors.joining("；"));
        String suggestionText = suggestions.isEmpty()
                ? "无"
                : suggestions.stream()
                .limit(3)
                .map(item -> item.priority() + ". " + defaultText(item.title()) + " - " + preview(item.description(), 80))
                .collect(Collectors.joining("；"));
        return """
                reviewId=%s
                状态=%s
                总结=%s
                评分=%s
                问题数=%s
                典型问题=%s
                优化建议=%s
                重构代码摘要=%s
                """.formatted(
                defaultText(detail.getReviewId()),
                detail.getStatus() == null ? "UNKNOWN" : detail.getStatus().name(),
                report == null ? "无" : preview(report.getSummary(), REVIEW_TEXT_PREVIEW_MAX),
                report == null ? 0 : report.getScore(),
                report == null ? 0 : report.getTotalIssues(),
                issueText,
                suggestionText,
                preview(detail.getRefactoredCode(), REVIEW_TEXT_PREVIEW_MAX)
        );
    }

    private String resolveProjectId(String sessionId, String requestedProjectId) {
        if (hasText(requestedProjectId)) {
            return requestedProjectId.trim();
        }
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

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String defaultText(String text) {
        return text == null ? "" : text;
    }

    private String preview(String text) {
        return preview(text, SESSION_PREVIEW_MAX);
    }

    private String preview(String text, int maxLength) {
        String normalized = defaultText(text).replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private long toEpochMilli(LocalDateTime value) {
        if (value == null) {
            return System.currentTimeMillis();
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static final class SessionAccumulator {
        private final String sessionId;
        private String projectId = "";
        private String latestReviewId = "";
        private String latestReviewStatus = "";
        private String language = "";
        private String latestMessagePreview = "";
        private long lastActivity;
        private long latestMessageTime;
        private long latestTaskTime;
        private int messageCount;
        private int reviewCount;

        private SessionAccumulator(String sessionId) {
            this.sessionId = sessionId;
        }

        private long lastActivity() {
            return lastActivity;
        }
    }
}
