package com.heima.codereview.api.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.codereview.common.exception.BizException;
import com.heima.codereview.common.model.review.ReviewIssue;
import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.common.model.review.ReviewStatus;
import com.heima.codereview.common.model.review.ReviewSubmitRequest;
import com.heima.codereview.common.model.review.ReviewSuggestion;
import com.heima.codereview.common.model.review.ReviewTaskDetail;
import com.heima.codereview.common.model.review.ReviewType;
import com.heima.codereview.common.model.review.SimilarCodeGroup;
import com.heima.codereview.common.persistence.entity.ReviewHistoryDO;
import com.heima.codereview.common.persistence.entity.ReviewIssueDO;
import com.heima.codereview.common.persistence.entity.ReviewSuggestionDO;
import com.heima.codereview.common.persistence.entity.ReviewTaskDO;
import com.heima.codereview.common.persistence.entity.SimilarCodeBlockDO;
import com.heima.codereview.common.persistence.entity.SimilarCodeGroupDO;
import com.heima.codereview.common.persistence.mapper.ReviewHistoryMapper;
import com.heima.codereview.common.persistence.mapper.ReviewIssueMapper;
import com.heima.codereview.common.persistence.mapper.ReviewSuggestionMapper;
import com.heima.codereview.common.persistence.mapper.ReviewTaskMapper;
import com.heima.codereview.common.persistence.mapper.SimilarCodeBlockMapper;
import com.heima.codereview.common.persistence.mapper.SimilarCodeGroupMapper;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.rag.knowledge.ReviewKnowledgeBase;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class ReviewTaskPersistenceRepository {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewSuggestionMapper reviewSuggestionMapper;
    private final SimilarCodeGroupMapper similarCodeGroupMapper;
    private final SimilarCodeBlockMapper similarCodeBlockMapper;
    private final ReviewHistoryMapper reviewHistoryMapper;
    private final ObjectMapper objectMapper;
    private final ReviewKnowledgeBase reviewKnowledgeBase;

    public ReviewTaskPersistenceRepository(ReviewTaskMapper reviewTaskMapper,
                                           ReviewIssueMapper reviewIssueMapper,
                                           ReviewSuggestionMapper reviewSuggestionMapper,
                                           SimilarCodeGroupMapper similarCodeGroupMapper,
                                           SimilarCodeBlockMapper similarCodeBlockMapper,
                                           ReviewHistoryMapper reviewHistoryMapper,
                                           ObjectMapper objectMapper,
                                           ReviewKnowledgeBase reviewKnowledgeBase) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewSuggestionMapper = reviewSuggestionMapper;
        this.similarCodeGroupMapper = similarCodeGroupMapper;
        this.similarCodeBlockMapper = similarCodeBlockMapper;
        this.reviewHistoryMapper = reviewHistoryMapper;
        this.objectMapper = objectMapper;
        this.reviewKnowledgeBase = reviewKnowledgeBase;
    }

    public void createTask(ReviewTaskDetail detail, ReviewSubmitRequest request) {
        ReviewTaskDO entity = new ReviewTaskDO();
        entity.setReviewId(detail.getReviewId());
        entity.setSessionId(detail.getSessionId());
        entity.setProjectId(request.projectId());
        entity.setReviewType((request.type() == null ? ReviewType.PASTE_CODE : request.type()).name());
        entity.setLanguage(request.language() == null || request.language().isBlank() ? "java" : request.language());
        entity.setTemplateId(request.templateId());
        entity.setStatus(detail.getStatus().name());
        entity.setScore(0);
        entity.setTotalIssues(0);
        entity.setSummary("");
        entity.setCodeContent(detail.getCodeContent());
        entity.setRefactoredCode("");
        entity.setStartedAt(LocalDateTime.now());
        reviewTaskMapper.insert(entity);
    }

    public ReviewTaskDetail findByReviewId(String reviewId) {
        ReviewTaskDO task = reviewTaskMapper.selectOne(new LambdaQueryWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getReviewId, reviewId)
                .last("limit 1"));
        if (task == null) {
            throw new BizException("审查任务不存在");
        }
        return toDetail(task);
    }

    public List<ReviewTaskDetail> findAll() {
        return reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTaskDO>()
                        .orderByDesc(ReviewTaskDO::getCreatedAt))
                .stream()
                .map(this::toDetail)
                .toList();
    }

    public void markCancelledBySession(String sessionId) {
        reviewTaskMapper.update(null, new LambdaUpdateWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getSessionId, sessionId)
                .eq(ReviewTaskDO::getStatus, ReviewStatus.PROCESSING.name())
                .set(ReviewTaskDO::getStatus, ReviewStatus.CANCELLED.name())
                .set(ReviewTaskDO::getCompletedAt, LocalDateTime.now()));
    }

    public void markFailed(String reviewId) {
        reviewTaskMapper.update(null, new LambdaUpdateWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getReviewId, reviewId)
                .set(ReviewTaskDO::getStatus, ReviewStatus.FAILED.name())
                .set(ReviewTaskDO::getCompletedAt, LocalDateTime.now()));
    }

    @Transactional
    public void deleteByReviewId(String reviewId) {
        clearChildren(reviewId);
        reviewHistoryMapper.delete(new LambdaQueryWrapper<ReviewHistoryDO>()
                .eq(ReviewHistoryDO::getReviewId, reviewId));
        reviewTaskMapper.delete(new LambdaQueryWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getReviewId, reviewId));
    }

    @Transactional
    public void saveCompletedTask(ReviewTaskDetail detail) {
        ReviewReport report = detail.getReport();
        reviewTaskMapper.update(null, new LambdaUpdateWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getReviewId, detail.getReviewId())
                .set(ReviewTaskDO::getStatus, ReviewStatus.COMPLETED.name())
                .set(ReviewTaskDO::getScore, report == null ? 0 : report.getScore())
                .set(ReviewTaskDO::getTotalIssues, report == null ? 0 : report.getTotalIssues())
                .set(ReviewTaskDO::getSummary, report == null ? "" : report.getSummary())
                .set(ReviewTaskDO::getRefactoredCode, detail.getRefactoredCode())
                .set(ReviewTaskDO::getCompletedAt, LocalDateTime.now()));

        clearChildren(detail.getReviewId());
        if (report == null) {
            return;
        }
        saveIssues(detail.getReviewId(), report.getIssues());
        saveSuggestions(detail.getReviewId(), report.getSuggestions());
        saveSimilarGroups(detail.getReviewId(), report.getSimilarCodeGroups());
    }

    public void saveReviewHistory(String reviewId,
                                  String sessionId,
                                  String projectId,
                                  String summary,
                                  Map<String, Object> metadata) {
        String metadataJson = writeMetadata(metadata);
        ReviewHistoryDO history = reviewHistoryMapper.selectOne(new LambdaQueryWrapper<ReviewHistoryDO>()
                .eq(ReviewHistoryDO::getReviewId, reviewId)
                .orderByDesc(ReviewHistoryDO::getCreatedAt)
                .last("limit 1"));
        if (history == null) {
            ReviewHistoryDO entity = new ReviewHistoryDO();
            entity.setRecordId(IdUtils.withPrefix("history"));
            entity.setReviewId(reviewId);
            entity.setSessionId(sessionId);
            entity.setProjectId(projectId);
            entity.setSummary(summary);
            entity.setEmbeddingModel("");
            entity.setEmbeddingDim(0);
            entity.setMetadata(metadataJson);
            entity.setCreatedAt(LocalDateTime.now());
            reviewHistoryMapper.insert(entity);
            return;
        }
        reviewHistoryMapper.update(null, new LambdaUpdateWrapper<ReviewHistoryDO>()
                .eq(ReviewHistoryDO::getId, history.getId())
                .set(ReviewHistoryDO::getSessionId, sessionId)
                .set(ReviewHistoryDO::getProjectId, projectId)
                .set(ReviewHistoryDO::getSummary, summary)
                .set(ReviewHistoryDO::getMetadata, metadataJson));
    }

    private void clearChildren(String reviewId) {
        List<SimilarCodeGroupDO> groups = similarCodeGroupMapper.selectList(new LambdaQueryWrapper<SimilarCodeGroupDO>()
                .eq(SimilarCodeGroupDO::getReviewId, reviewId));
        for (SimilarCodeGroupDO group : groups) {
            similarCodeBlockMapper.delete(new LambdaQueryWrapper<SimilarCodeBlockDO>()
                    .eq(SimilarCodeBlockDO::getGroupId, group.getGroupId()));
        }
        similarCodeGroupMapper.delete(new LambdaQueryWrapper<SimilarCodeGroupDO>()
                .eq(SimilarCodeGroupDO::getReviewId, reviewId));
        reviewSuggestionMapper.delete(new LambdaQueryWrapper<ReviewSuggestionDO>()
                .eq(ReviewSuggestionDO::getReviewId, reviewId));
        reviewIssueMapper.delete(new LambdaQueryWrapper<ReviewIssueDO>()
                .eq(ReviewIssueDO::getReviewId, reviewId));
    }

    private void saveIssues(String reviewId, List<ReviewIssue> issues) {
        for (ReviewIssue issue : issues) {
            ReviewIssueDO entity = new ReviewIssueDO();
            entity.setIssueId(issue.id() == null || issue.id().isBlank() ? IdUtils.withPrefix("issue") : issue.id());
            entity.setReviewId(reviewId);
            entity.setSeverity(issue.severity());
            entity.setFilePath(issue.file());
            entity.setLineNumber(issue.lineNumber());
            entity.setMessage(issue.message());
            entity.setRuleId(issue.ruleId());
            entity.setSuggestion(issue.suggestion());
            reviewIssueMapper.insert(entity);
        }
    }

    private void saveSuggestions(String reviewId, List<ReviewSuggestion> suggestions) {
        for (ReviewSuggestion suggestion : suggestions) {
            ReviewSuggestionDO entity = new ReviewSuggestionDO();
            entity.setReviewId(reviewId);
            entity.setPriorityNo(suggestion.priority());
            entity.setTitle(suggestion.title());
            entity.setDescription(suggestion.description());
            reviewSuggestionMapper.insert(entity);
        }
    }

    private void saveSimilarGroups(String reviewId, List<SimilarCodeGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        int index = 1;
        for (SimilarCodeGroup group : groups) {
            String storageGroupId = buildStorageGroupId(reviewId, group.groupId(), index++);
            SimilarCodeGroupDO groupEntity = new SimilarCodeGroupDO();
            groupEntity.setGroupId(storageGroupId);
            groupEntity.setReviewId(reviewId);
            groupEntity.setSimilarity(BigDecimal.valueOf(group.similarity()));
            similarCodeGroupMapper.insert(groupEntity);

            List<String> codeBlocks = group.codeBlocks() == null ? List.of() : group.codeBlocks();
            for (String block : codeBlocks) {
                SimilarCodeBlockDO blockEntity = new SimilarCodeBlockDO();
                blockEntity.setGroupId(storageGroupId);
                blockEntity.setBlockContent(block);
                similarCodeBlockMapper.insert(blockEntity);
            }
        }
    }

    private String buildStorageGroupId(String reviewId, String groupId, int index) {
        String safeGroupId = groupId == null || groupId.isBlank() ? "group-" + index : groupId;
        if (safeGroupId.length() > 32) {
            safeGroupId = "group-" + index;
        }
        return reviewId + "-" + safeGroupId;
    }

    public Map<String, String> loadAgentOutputs(String reviewId) {
        Map<String, String> historyOutputs = extractAgentOutputs(loadHistoryMetadata(reviewId));
        if (!historyOutputs.isEmpty()) {
            return historyOutputs;
        }
        return reviewKnowledgeBase.findMetadataByReviewId(reviewId)
                .map(this::extractAgentOutputs)
                .orElse(Map.of());
    }

    private ReviewTaskDetail toDetail(ReviewTaskDO task) {
        ReviewTaskDetail detail = new ReviewTaskDetail();
        detail.setReviewId(task.getReviewId());
        detail.setSessionId(task.getSessionId());
        detail.setStatus(ReviewStatus.valueOf(task.getStatus()));
        detail.setCodeContent(task.getCodeContent());
        detail.setRefactoredCode(task.getRefactoredCode());
        detail.setAgentOutputs(loadAgentOutputs(task.getReviewId()));

        ReviewReport report = new ReviewReport();
        report.setReviewId(task.getReviewId());
        report.setSummary(task.getSummary());
        report.setScore(task.getScore() == null ? 0 : task.getScore());
        report.setTotalIssues(task.getTotalIssues() == null ? 0 : task.getTotalIssues());
        report.setIssues(loadIssues(task.getReviewId()));
        report.setSuggestions(loadSuggestions(task.getReviewId()));
        report.setSimilarCodeGroups(loadSimilarGroups(task.getReviewId()));
        report.setCriticalCount((int) report.getIssues().stream().filter(item -> "CRITICAL".equals(item.severity())).count());
        report.setHighCount((int) report.getIssues().stream().filter(item -> "HIGH".equals(item.severity())).count());
        report.setMediumCount((int) report.getIssues().stream().filter(item -> "MEDIUM".equals(item.severity())).count());
        report.setLowCount((int) report.getIssues().stream().filter(item -> "LOW".equals(item.severity())).count());
        detail.setReport(report);
        return detail;
    }

    private Map<String, Object> loadHistoryMetadata(String reviewId) {
        ReviewHistoryDO history = reviewHistoryMapper.selectOne(new LambdaQueryWrapper<ReviewHistoryDO>()
                .eq(ReviewHistoryDO::getReviewId, reviewId)
                .orderByDesc(ReviewHistoryDO::getCreatedAt)
                .last("limit 1"));
        if (history == null || history.getMetadata() == null || history.getMetadata().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(history.getMetadata(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> extractAgentOutputs(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Object outputs = metadata.get("agentOutputs");
        if (!(outputs instanceof Map<?, ?> outputMap)) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        outputMap.forEach((key, value) -> result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        return result;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<ReviewIssue> loadIssues(String reviewId) {
        return reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueDO>()
                        .eq(ReviewIssueDO::getReviewId, reviewId))
                .stream()
                .map(item -> new ReviewIssue(
                        item.getIssueId(),
                        item.getSeverity(),
                        item.getFilePath(),
                        item.getLineNumber() == null ? 0 : item.getLineNumber(),
                        item.getMessage(),
                        item.getRuleId(),
                        item.getSuggestion()
                ))
                .toList();
    }

    private List<ReviewSuggestion> loadSuggestions(String reviewId) {
        return reviewSuggestionMapper.selectList(new LambdaQueryWrapper<ReviewSuggestionDO>()
                        .eq(ReviewSuggestionDO::getReviewId, reviewId)
                        .orderByAsc(ReviewSuggestionDO::getPriorityNo))
                .stream()
                .map(item -> new ReviewSuggestion(item.getPriorityNo(), item.getTitle(), item.getDescription()))
                .toList();
    }

    private List<SimilarCodeGroup> loadSimilarGroups(String reviewId) {
        List<SimilarCodeGroupDO> groups = similarCodeGroupMapper.selectList(new LambdaQueryWrapper<SimilarCodeGroupDO>()
                .eq(SimilarCodeGroupDO::getReviewId, reviewId));
        List<SimilarCodeGroup> result = new ArrayList<>();
        for (SimilarCodeGroupDO group : groups) {
            List<String> blocks = similarCodeBlockMapper.selectList(new LambdaQueryWrapper<SimilarCodeBlockDO>()
                            .eq(SimilarCodeBlockDO::getGroupId, group.getGroupId()))
                    .stream()
                    .map(SimilarCodeBlockDO::getBlockContent)
                    .collect(Collectors.toList());
            result.add(new SimilarCodeGroup(group.getGroupId(), group.getSimilarity().doubleValue(), blocks));
        }
        return result;
    }
}
