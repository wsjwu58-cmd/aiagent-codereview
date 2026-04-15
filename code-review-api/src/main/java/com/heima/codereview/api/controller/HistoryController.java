package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.ChatService;
import com.heima.codereview.common.model.history.HistorySearchResponse;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final ChatService chatService;

    public HistoryController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/search")
    public ApiResponse<HistorySearchResponse> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                     @RequestParam(value = "sessionId", required = false) String sessionId,
                                                     @RequestParam(value = "projectId", required = false) String projectId) {
        return ApiResponse.success(chatService.searchHistory(keyword, sessionId, projectId));
    }
}