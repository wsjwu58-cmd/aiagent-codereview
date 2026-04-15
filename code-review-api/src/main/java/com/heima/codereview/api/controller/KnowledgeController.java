package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.KnowledgeService;
import com.heima.codereview.common.model.knowledge.KnowledgeSearchResponse;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(@RequestParam(value = "query", required = false) String query,
                                                       @RequestParam(value = "projectId", required = false) String projectId,
                                                       @RequestParam(value = "sessionId", required = false) String sessionId,
                                                       @RequestParam(value = "topK", required = false) Integer topK) {
        return ApiResponse.success(knowledgeService.search(query, projectId, sessionId, topK));
    }
}
