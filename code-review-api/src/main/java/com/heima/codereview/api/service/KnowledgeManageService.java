package com.heima.codereview.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.codereview.api.persistence.ReviewTaskPersistenceRepository;
import com.heima.codereview.common.model.knowledge.KnowledgeBatchDeleteRequest;
import com.heima.codereview.common.model.knowledge.KnowledgeDeleteItem;
import com.heima.codereview.common.model.knowledge.KnowledgeManagePageResponse;
import com.heima.codereview.common.model.knowledge.KnowledgeManageRecord;
import com.heima.codereview.common.model.knowledge.KnowledgeRecordType;
import com.heima.codereview.common.persistence.entity.ChatMessageDO;
import com.heima.codereview.common.persistence.entity.ReviewTaskDO;
import com.heima.codereview.common.persistence.mapper.ChatMessageMapper;
import com.heima.codereview.common.persistence.mapper.ReviewTaskMapper;
import com.heima.codereview.rag.ChatHistoryRepository;
import com.heima.codereview.rag.PdfRepository;
import com.heima.codereview.rag.knowledge.ReviewKnowledgeBase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeManageService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final PdfRepository pdfRepository;
    private final ReviewKnowledgeBase reviewKnowledgeBase;
    private final ReviewTaskPersistenceRepository reviewTaskPersistenceRepository;
    private final ChatHistoryRepository chatHistoryRepository;

    public KnowledgeManageService(ReviewTaskMapper reviewTaskMapper,
                                  ChatMessageMapper chatMessageMapper,
                                  PdfRepository pdfRepository,
                                  ReviewKnowledgeBase reviewKnowledgeBase,
                                  ReviewTaskPersistenceRepository reviewTaskPersistenceRepository,
                                  ChatHistoryRepository chatHistoryRepository) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.pdfRepository = pdfRepository;
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.reviewTaskPersistenceRepository = reviewTaskPersistenceRepository;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    public KnowledgeManagePageResponse listRecords(KnowledgeRecordType type,
                                                   String projectId,
                                                   String keyword,
                                                   int page,
                                                   int size) {
        int realPage = Math.max(0, page);
        int realSize = Math.max(1, Math.min(size, 100));
        List<KnowledgeManageRecord> merged = new ArrayList<>();
        if (type == null || type == KnowledgeRecordType.REVIEW_HISTORY) {
            merged.addAll(loadReviewRecords(projectId, keyword));
        }
        if (type == null || type == KnowledgeRecordType.PDF_NORM) {
            merged.addAll(loadPdfRecords(projectId, keyword));
        }
        if (type == null || type == KnowledgeRecordType.CHAT_HISTORY) {
            merged.addAll(loadChatRecords(projectId, keyword));
        }
        merged.sort(Comparator.comparingLong(KnowledgeManageRecord::createdAt).reversed());

        long total = merged.size();
        int fromIndex = Math.min(realPage * realSize, merged.size());
        int toIndex = Math.min(fromIndex + realSize, merged.size());
        int totalPages = (int) Math.ceil(total / (double) realSize);

        return new KnowledgeManagePageResponse(
                merged.subList(fromIndex, toIndex),
                total,
                totalPages,
                realPage,
                realSize
        );
    }

    @Transactional
    public void deleteRecord(String id, KnowledgeRecordType type) {
        if (id == null || id.isBlank()) {
            return;
        }
        KnowledgeRecordType resolvedType = type == null ? inferType(id) : type;
        if (resolvedType == null) {
            return;
        }
        switch (resolvedType) {
            case REVIEW_HISTORY -> {
                reviewTaskPersistenceRepository.deleteByReviewId(id);
                reviewKnowledgeBase.deleteByReviewId(id);
            }
            case PDF_NORM -> pdfRepository.deleteNorm(id);
            case CHAT_HISTORY -> {
                chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getMessageId, id));
                chatHistoryRepository.deleteByMessageId(id);
            }
        }
    }

    @Transactional
    public void batchDelete(KnowledgeBatchDeleteRequest request) {
        if (request == null || request.records() == null) {
            return;
        }
        for (KnowledgeDeleteItem item : request.records()) {
            if (item == null) {
                continue;
            }
            deleteRecord(item.id(), item.type());
        }
    }

    private List<KnowledgeManageRecord> loadReviewRecords(String projectId, String keyword) {
        return reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTaskDO>()
                        .orderByDesc(ReviewTaskDO::getUpdatedAt)
                        .orderByDesc(ReviewTaskDO::getCreatedAt))
                .stream()
                .filter(task -> projectId == null || projectId.isBlank() || Objects.equals(projectId, task.getProjectId()))
                .filter(task -> matchesKeyword(keyword, task.getReviewId(), task.getSummary(), task.getProjectId()))
                .map(task -> new KnowledgeManageRecord(
                        task.getReviewId(),
                        KnowledgeRecordType.REVIEW_HISTORY,
                        defaultString(task.getProjectId()),
                        defaultString(task.getSessionId()),
                        defaultString(task.getReviewId()),
                        "",
                        defaultString(task.getSummary()),
                        toEpochMilli(firstNonNull(task.getUpdatedAt(), task.getCompletedAt(), task.getCreatedAt(), task.getStartedAt())),
                        Map.of(
                                "score", task.getScore() == null ? 0 : task.getScore(),
                                "totalIssues", task.getTotalIssues() == null ? 0 : task.getTotalIssues(),
                                "status", defaultString(task.getStatus())
                        )
                ))
                .toList();
    }

    private List<KnowledgeManageRecord> loadPdfRecords(String projectId, String keyword) {
        return pdfRepository.listNorms(projectId).stream()
                .filter(item -> matchesKeyword(keyword, item.fileId(), item.fileName(), item.description()))
                .map(item -> new KnowledgeManageRecord(
                        item.fileId(),
                        KnowledgeRecordType.PDF_NORM,
                        defaultString(item.projectId()),
                        "",
                        "",
                        defaultString(item.fileName()),
                        defaultString(item.description()).isBlank() ? defaultString(item.fileName()) : defaultString(item.description()),
                        item.uploadedAt(),
                        Map.of("pageCount", item.pageCount())
                ))
                .toList();
    }

    private List<KnowledgeManageRecord> loadChatRecords(String projectId, String keyword) {
        Set<String> allowedSessions = resolveProjectSessions(projectId);
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                        .orderByDesc(ChatMessageDO::getCreatedAt))
                .stream()
                .filter(item -> allowedSessions.isEmpty() || allowedSessions.contains(item.getSessionId()))
                .filter(item -> matchesKeyword(keyword, item.getMessageId(), item.getContent(), item.getSessionId()))
                .map(item -> new KnowledgeManageRecord(
                        item.getMessageId(),
                        KnowledgeRecordType.CHAT_HISTORY,
                        "",
                        defaultString(item.getSessionId()),
                        "",
                        "",
                        preview(item.getContent()),
                        toEpochMilli(item.getCreatedAt()),
                        Map.of("role", defaultString(item.getRole()))
                ))
                .toList();
    }

    private Set<String> resolveProjectSessions(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Set.of();
        }
        Set<String> sessionIds = new HashSet<>();
        reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTaskDO>()
                        .eq(ReviewTaskDO::getProjectId, projectId))
                .forEach(task -> sessionIds.add(defaultString(task.getSessionId())));
        return sessionIds;
    }

    private KnowledgeRecordType inferType(String id) {
        if (id.startsWith("review-")) {
            return KnowledgeRecordType.REVIEW_HISTORY;
        }
        if (id.startsWith("msg-")) {
            return KnowledgeRecordType.CHAT_HISTORY;
        }
        return KnowledgeRecordType.PDF_NORM;
    }

    private boolean matchesKeyword(String keyword, String... values) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String preview(String value) {
        String normalized = defaultString(value).replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
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
}
