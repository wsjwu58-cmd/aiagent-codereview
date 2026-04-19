package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.LocalCodeAnalysisService;
import com.heima.codereview.common.model.local.LocalCodeAnalyzeRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/local-code")
public class LocalCodeController {

    private final LocalCodeAnalysisService localCodeAnalysisService;

    public LocalCodeController(LocalCodeAnalysisService localCodeAnalysisService) {
        this.localCodeAnalysisService = localCodeAnalysisService;
    }

    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyze(@RequestBody LocalCodeAnalyzeRequest request) {
        return localCodeAnalysisService.analyze(request);
    }
}
