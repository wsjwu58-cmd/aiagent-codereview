package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.ChatService;
import com.heima.codereview.api.service.ConversationalService;
import com.heima.codereview.common.model.chat.ChatMessage;
import com.heima.codereview.common.model.chat.ChatReply;
import com.heima.codereview.common.model.chat.ChatSendRequest;
import com.heima.codereview.common.model.chat.ReactChatRequest;
import com.heima.codereview.common.model.norm.NormRecord;
import com.heima.codereview.common.model.norm.NormSummary;
import com.heima.codereview.common.model.norm.NormUploadResult;
import com.heima.codereview.common.model.session.SessionSummary;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ConversationalService conversationalService;

    public ChatController(ChatService chatService, ConversationalService conversationalService) {
        this.chatService = chatService;
        this.conversationalService = conversationalService;
    }

    @PostMapping("/send")
    public ApiResponse<ChatReply> send(@RequestBody ChatSendRequest request) {
        return ApiResponse.success(conversationalService.send(request));
    }

    @PostMapping(value = "/react", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter react(@RequestBody ReactChatRequest request) {

        return conversationalService.react(request);
    }

    @PostMapping(value = "/upload-norm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NormUploadResult> uploadNorm(@RequestPart("file") MultipartFile file,
                                                    @RequestParam("projectId") String projectId,
                                                    @RequestParam(value = "description", required = false) String description) {
        return ApiResponse.success(conversationalService.uploadNorm(file, projectId, description));
    }

    @GetMapping("/search-norms")
    public ApiResponse<List<NormRecord>> searchNorms(@RequestParam("query") String query,
                                                     @RequestParam(value = "projectId", required = false) String projectId,
                                                     @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ApiResponse.success(conversationalService.searchNorms(query, projectId, limit));
    }

    @GetMapping("/norms")
    public ApiResponse<List<NormSummary>> norms(@RequestParam(value = "projectId", required = false) String projectId) {
        return ApiResponse.success(conversationalService.listNorms(projectId));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummary>> sessions() {
        return ApiResponse.success(chatService.listSessions());
    }

    @GetMapping("/messages")
    public ApiResponse<List<ChatMessage>> messages(@RequestParam("sessionId") String sessionId) {
        return ApiResponse.success(chatService.listMessages(sessionId));
    }
}
