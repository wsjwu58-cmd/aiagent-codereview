package com.heima.codereview.api.service;

import com.heima.codereview.common.model.review.BatchReviewRequest;
import com.heima.codereview.common.model.review.BatchReviewResponse;
import com.heima.codereview.common.model.review.IncrementalReviewRequest;
import com.heima.codereview.common.model.review.IncrementalReviewResponse;
import com.heima.codereview.common.model.review.ReviewStatus;
import com.heima.codereview.common.model.review.ReviewSubmitRequest;
import com.heima.codereview.common.model.review.ReviewType;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.tools.git.GitDiffFetcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class ReviewEnhancementService {

    private final ReviewService reviewService;
    private final GitDiffFetcher gitDiffFetcher;
    private final Executor reviewTaskExecutor;

    public ReviewEnhancementService(ReviewService reviewService,
                                    GitDiffFetcher gitDiffFetcher,
                                    @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor) {
        this.reviewService = reviewService;
        this.gitDiffFetcher = gitDiffFetcher;
        this.reviewTaskExecutor = reviewTaskExecutor;
    }

    public BatchReviewResponse batchSubmit(BatchReviewRequest request) {
        String batchId = IdUtils.withPrefix("batch");
        Map<String, String> statuses = new ConcurrentHashMap<>();

        if (request == null || request.submissions() == null || request.submissions().isEmpty()) {
            return new BatchReviewResponse(batchId, statuses);
        }

        request.submissions().forEach(submission -> {
            Runnable task = () -> {
                try {
                    var summary = reviewService.submit(new ReviewSubmitRequest(
                            ReviewType.PASTE_CODE,
                            null,
                            null,
                            submission.content(),
                            batchId,
                            null,
                            submission.language(),
                            request.templateId()
                    ));
                    statuses.put(submission.taskId(), summary.status().name());
                } catch (Exception e) {
                    statuses.put(submission.taskId(), "FAILED");
                }
            };
            if (request.parallel()) {
                reviewTaskExecutor.execute(task);
            } else {
                task.run();
            }
        });

        return new BatchReviewResponse(batchId, statuses);
    }

    public IncrementalReviewResponse incrementalReview(IncrementalReviewRequest request) {
        String diff = gitDiffFetcher.fetchDiff(
                request.repoUrl(),
                request.branch(),
                request.baseCommit(),
                request.headCommit(),
                request.language()
        );
        var summary = reviewService.submit(new ReviewSubmitRequest(
                ReviewType.GIT_DIFF,
                request.repoUrl(),
                request.branch(),
                diff,
                request.projectId(),
                request.sessionId(),
                request.language(),
                null
        ));

        return new IncrementalReviewResponse(
                summary.reviewId(),
                ReviewStatus.PROCESSING,
                Arrays.stream(diff.split("\\n"))
                        .filter(line -> line.startsWith("+") || line.startsWith("-"))
                        .limit(20)
                        .toList()
        );
    }
}
