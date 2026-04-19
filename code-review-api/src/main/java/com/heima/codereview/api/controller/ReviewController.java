package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.ReviewEnhancementService;
import com.heima.codereview.api.service.ReviewService;
import com.heima.codereview.api.service.ReviewTemplateService;
import com.heima.codereview.api.service.PdfExportService;
import com.heima.codereview.common.model.review.BatchReviewRequest;
import com.heima.codereview.common.model.review.BatchReviewResponse;
import com.heima.codereview.common.model.review.IncrementalReviewRequest;
import com.heima.codereview.common.model.review.IncrementalReviewResponse;
import com.heima.codereview.common.model.review.ReviewSubmitRequest;
import com.heima.codereview.common.model.review.ReviewTaskDetail;
import com.heima.codereview.common.model.review.ReviewTaskSummary;
import com.heima.codereview.common.model.template.ReviewTemplate;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewEnhancementService reviewEnhancementService;
    private final ReviewTemplateService reviewTemplateService;
    private final PdfExportService pdfExportService;

    public ReviewController(ReviewService reviewService,
                            ReviewEnhancementService reviewEnhancementService,
                            ReviewTemplateService reviewTemplateService,
                            PdfExportService pdfExportService) {
        this.reviewService = reviewService;
        this.reviewEnhancementService = reviewEnhancementService;
        this.reviewTemplateService = reviewTemplateService;
        this.pdfExportService = pdfExportService;
    }

    @PostMapping("/submit")
    public ApiResponse<ReviewTaskSummary> submit(@RequestBody ReviewSubmitRequest request) {
        return ApiResponse.success(reviewService.submit(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewTaskDetail> detail(@PathVariable("id") String reviewId) {
        return ApiResponse.success(reviewService.getById(reviewId));
    }

    @PostMapping("/cancel")
    public ApiResponse<Map<String, String>> cancel(@RequestParam("sessionId") String sessionId) {
        reviewService.cancelReview(sessionId);
        return ApiResponse.success(Map.of("sessionId", sessionId, "status", "CANCELLED"));
    }

    @PostMapping("/batch")
    public ApiResponse<BatchReviewResponse> batch(@RequestBody BatchReviewRequest request) {
        return ApiResponse.success(reviewEnhancementService.batchSubmit(request));
    }

    @PostMapping("/incremental")
    public ApiResponse<IncrementalReviewResponse> incremental(@RequestBody IncrementalReviewRequest request) {
        return ApiResponse.success(reviewEnhancementService.incrementalReview(request));
    }

    @GetMapping("/templates")
    public ApiResponse<List<ReviewTemplate>> templates() {
        return ApiResponse.success(reviewTemplateService.listBuiltInTemplates());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable("id") String reviewId) {
        byte[] pdfBytes = pdfExportService.exportReview(reviewId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"review_%s.pdf\"".formatted(reviewId))
                .body(pdfBytes);
    }
}
