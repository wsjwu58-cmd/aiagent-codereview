package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.KnowledgeManageService;
import com.heima.codereview.common.model.knowledge.KnowledgeBatchDeleteRequest;
import com.heima.codereview.common.model.knowledge.KnowledgeManagePageResponse;
import com.heima.codereview.common.model.knowledge.KnowledgeRecordType;
import com.heima.codereview.api.service.KnowledgeService;
import com.heima.codereview.common.model.knowledge.KnowledgeSearchResponse;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeManageService knowledgeManageService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeManageService knowledgeManageService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeManageService = knowledgeManageService;
    }

    @GetMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(@RequestParam(value = "query", required = false) String query,
                                                       @RequestParam(value = "projectId", required = false) String projectId,
                                                       @RequestParam(value = "sessionId", required = false) String sessionId,
                                                       @RequestParam(value = "topK", required = false) Integer topK) {
        return ApiResponse.success(knowledgeService.search(query, projectId, sessionId, topK));
    }

    @GetMapping("/records")
    public ApiResponse<KnowledgeManagePageResponse> records(@RequestParam(value = "type", required = false) KnowledgeRecordType type,
                                                            @RequestParam(value = "projectId", required = false) String projectId,
                                                            @RequestParam(value = "keyword", required = false) String keyword,
                                                            @RequestParam(value = "page", defaultValue = "0") int page,
                                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(knowledgeManageService.listRecords(type, projectId, keyword, page, size));
    }

    @DeleteMapping("/records/{id}")
    public ApiResponse<Boolean> deleteRecord(@PathVariable("id") String id,
                                             @RequestParam(value = "type", required = false) KnowledgeRecordType type) {
        knowledgeManageService.deleteRecord(id, type);
        return ApiResponse.success(Boolean.TRUE);
    }

    @PostMapping("/records/batch")
    public ApiResponse<Boolean> batchDelete(@RequestBody KnowledgeBatchDeleteRequest request) {
        knowledgeManageService.batchDelete(request);
        return ApiResponse.success(Boolean.TRUE);
    }
}
