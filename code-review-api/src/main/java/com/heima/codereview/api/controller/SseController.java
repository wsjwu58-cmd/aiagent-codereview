package com.heima.codereview.api.controller;

import com.heima.codereview.api.sse.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final SseEmitterManager sseEmitterManager;

    public SseController(SseEmitterManager sseEmitterManager) {
        this.sseEmitterManager = sseEmitterManager;
    }

    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("sessionId") String sessionId) throws IOException {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseEmitterManager.register(sessionId, emitter);
        emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("sessionId", sessionId, "timestamp", System.currentTimeMillis())));
        emitter.onTimeout(() -> sseEmitterManager.remove(sessionId));
        emitter.onCompletion(() -> sseEmitterManager.remove(sessionId));
        return emitter;
    }
}
